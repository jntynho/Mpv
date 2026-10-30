package live.mehiz.mpvkt.ui.secure

import android.app.Application
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SecureVaultTest {
  private val context
    get() = ApplicationProvider.getApplicationContext<Application>()

  private val wrapping = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")

  private fun vault() = SecureVault(context) { wrapping }

  @Before
  fun before() {
    File(context.noBackupFilesDir, "secure-vault").deleteRecursively()
    context.getSharedPreferences("secure_vault_attempts", 0).edit().clear().commit()
    context.getSharedPreferences("secure_folder_ui", 0).edit().clear().commit()
  }

  @After
  fun after() {
    before()
    SecureVault.clearPlaybackCache(context)
  }

  @Test
  fun cryptoRejectsTamperingWrongKeyAndSwappedIdentity() {
    val bytes = "Private metadata".toByteArray()
    val sealed = VaultCrypto.seal(wrapping, bytes, "identity")
    assertArrayEquals(bytes, VaultCrypto.open(wrapping, sealed, "identity"))
    assertTrue(runCatching { VaultCrypto.open(wrapping, sealed, "other") }.isFailure)
    assertTrue(
      runCatching { VaultCrypto.open(SecretKeySpec(ByteArray(32), "AES"), sealed, "identity") }
        .isFailure
    )
    sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
    assertTrue(runCatching { VaultCrypto.open(wrapping, sealed, "identity") }.isFailure)
  }

  @Test
  fun vaultEncryptsNamesAndLargeChunkedMediaThenLocksAndRestoresExactly() =
    runBlocking<Unit> {
      val source = File(context.cacheDir, "private-title.mp4")
      val data = ByteArray(260000) { (it % 251).toByte() }
      source.writeBytes(data)
      vault().use { vault ->
        vault.setup("123456".toCharArray())
        vault.add(source.toUri(), source.name)
        val entry = vault.entries().single()
        val root = File(context.noBackupFilesDir, "secure-vault")
        assertFalse(File(root, "index").readText().contains(source.name))
        assertFalse(File(root, entry.id).readBytes().contentEquals(data))
        assertArrayEquals(data, vault.materialize(entry).readBytes())
        assertArrayEquals(data, source.readBytes())
        vault.close()
        assertFalse(vault.unlocked)
        assertTrue(runCatching { vault.entries() }.isFailure)
        assertTrue(runCatching { vault.unlock("111111".toCharArray()) }.isFailure)
        assertFalse(vault.unlocked)
        vault.unlock("123456".toCharArray())
        assertEquals(entry, vault.entries().single())
        vault.delete(entry)
        assertTrue(vault.entries().isEmpty())
        assertTrue(source.exists())
      }
      source.delete()
    }

  @Test
  fun truncatedCiphertextNeverExposesADecryptedPlaybackFile() =
    runBlocking<Unit> {
      val source =
        File(context.cacheDir, "source.mp4").apply { writeBytes(ByteArray(90000) { 42 }) }
      vault().use { vault ->
        vault.setup("123456".toCharArray())
        vault.add(source.toUri(), "Private")
        val entry = vault.entries().single()
        val file = File(context.noBackupFilesDir, "secure-vault/${entry.id}")
        file.writeBytes(file.readBytes().dropLast(1).toByteArray())
        assertTrue(runCatching { vault.materialize(entry) }.isFailure)
        assertTrue(File(context.cacheDir, "secure-playback").listFiles().orEmpty().isEmpty())
        assertEquals(entry, vault.entries().single())
      }
      source.delete()
    }

  @Test
  fun recoveryAndPinChangesKeepLegacyCiphertextReadable() =
    runBlocking<Unit> {
      val source =
        File(context.cacheDir, "legacy.mp4").apply { writeBytes(ByteArray(90000) { it.toByte() }) }
      val root = File(context.noBackupFilesDir, "secure-vault")
      vault().use { v ->
        v.setup("123456".toCharArray())
        val entry = v.add(source.toUri(), "Legacy film")
        // Reproduce the v0.20 index, where origin and MIME fields did not exist.
        val envelope =
          VaultCrypto.open(wrapping, File(root, "account").readBytes(), "mpv-vault-device-v1")
        val pinKey = VaultCrypto.derive("123456".toCharArray(), envelope.copyOfRange(0, 32))
        val dataKey =
          VaultCrypto.open(
            SecretKeySpec(pinKey, "AES"),
            envelope.copyOfRange(32, envelope.size),
            "mpv-vault-pin-v1",
          )
        val oldIndex = """[{"id":"${entry.id}","name":"Legacy film","bytes":90000}]"""
        File(root, "index")
          .writeBytes(
            VaultCrypto.seal(
              SecretKeySpec(dataKey, "AES"),
              oldIndex.toByteArray(),
              "mpv-vault-index-v1",
            )
          )
        v.close()
        v.unlock("123456".toCharArray())
        val legacy = v.entries().single()
        assertEquals("", legacy.sourceUri)
        v.setRecovery("First pet?", "  MiMi  ".toCharArray())
        assertEquals("First pet?", v.recoveryQuestion())
        v.close()
        assertTrue(
          runCatching { v.recoverPin("wrong".toCharArray(), "654321".toCharArray()) }.isFailure
        )
        assertFalse(v.unlocked)
        v.recoverPin("mimi".toCharArray(), "4321".toCharArray())
        assertEquals(4, v.pinLength)
        assertArrayEquals(source.readBytes(), v.materialize(legacy).readBytes())
        v.close()
        assertTrue(runCatching { v.unlock("123456".toCharArray()) }.isFailure)
        v.unlock("4321".toCharArray())
        v.changePin("123456".toCharArray())
        assertEquals(6, v.pinLength)
        v.close()
        v.unlock("123456".toCharArray())
        assertEquals(legacy, v.entries().single())
      }
      source.delete()
    }

  @Test
  fun moveNeverDeletesTheOriginalUntilTheEncryptedCopyIsVerified() =
    runBlocking<Unit> {
      val source = File(context.cacheDir, "move.mp4").apply { writeBytes(ByteArray(90000) { 72 }) }
      var deletes = 0
      lateinit var verify: () -> Unit
      val provider =
        object : android.content.ContentProvider() {
          override fun onCreate() = true

          override fun getType(uri: android.net.Uri) = "video/mp4"

          override fun query(
            uri: android.net.Uri,
            p: Array<out String>?,
            s: String?,
            a: Array<out String>?,
            o: String?,
          ): android.database.Cursor? = null

          override fun insert(
            uri: android.net.Uri,
            v: android.content.ContentValues?,
          ): android.net.Uri? = null

          override fun update(
            uri: android.net.Uri,
            v: android.content.ContentValues?,
            s: String?,
            a: Array<out String>?,
          ) = 0

          override fun openFile(uri: android.net.Uri, mode: String) =
            android.os.ParcelFileDescriptor.open(
              source,
              android.os.ParcelFileDescriptor.MODE_READ_ONLY,
            )

          override fun delete(uri: android.net.Uri, s: String?, a: Array<out String>?): Int {
            verify()
            deletes++
            return if (source.delete()) 1 else 0
          }
        }
      provider.attachInfo(
        context,
        android.content.pm.ProviderInfo().apply {
          authority = "vault.test"
          applicationInfo = context.applicationInfo
          packageName = context.packageName
          exported = true
        },
      )
      org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("vault.test", provider)
      vault().use { v ->
        v.setup("123456".toCharArray())
        val entry = v.add(android.net.Uri.parse("content://vault.test/film"), "Film")
        assertEquals(0, deletes)
        assertTrue(source.exists())
        verify = {
          assertEquals(entry, v.entries().single())
          assertArrayEquals(source.readBytes(), runBlocking { v.materialize(entry) }.readBytes())
        }
        assertTrue(v.moveOriginal(entry))
        assertEquals(1, deletes)
        assertFalse(source.exists())
        assertTrue(v.entries().single().sourceRemoved)
      }
    }

  @Test
  fun deniedDeletionAndFailedRestoreLeaveTheEncryptedFileUsable() =
    runBlocking<Unit> {
      val source = File(context.cacheDir, "keep.mp4").apply { writeBytes(ByteArray(1000) { 9 }) }
      vault().use { v ->
        v.setup("1234".toCharArray())
        val entry = v.add(source.toUri(), "Keep")
        assertFalse(v.moveOriginal(entry))
        assertTrue(source.exists())
        val invalidTree =
          android.provider.DocumentsContract.buildTreeDocumentUri("no-provider", "root")
        assertTrue(runCatching { v.restore(entry, invalidTree) }.isFailure)
        assertEquals(entry, v.entries().single())
        assertArrayEquals(source.readBytes(), v.materialize(entry).readBytes())
      }
      source.delete()
    }
}
