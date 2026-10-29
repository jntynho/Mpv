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
}
