package live.mehiz.mpvkt.ui.secure

import android.app.Application
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
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
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VaultRestoreTest {
  private val context
    get() = ApplicationProvider.getApplicationContext<Application>()

  @Before
  fun clear() {
    File(context.noBackupFilesDir, "secure-vault").deleteRecursively()
    context.getSharedPreferences("secure_vault_attempts", 0).edit().clear().commit()
  }

  @Test
  fun restoreVerifiesTheDestinationBeforeRemovingItsEncryptedCopy() =
    runBlocking<Unit> { exercise(false) }

  @Test
  fun failedReadBackDeletesTheBadPublicCopyAndKeepsTheEncryptedOriginal() =
    runBlocking<Unit> { exercise(true) }

  private suspend fun exercise(corrupt: Boolean) {
    val target = File(context.cacheDir, "restored.mp4")
    val tree = DocumentsContract.buildTreeDocumentUri("vault.restore", "root")
    val fileUri = DocumentsContract.buildDocumentUriUsingTree(tree, "restored")
    val provider =
      object : ContentProvider() {
        override fun onCreate() = true

        override fun query(
          uri: Uri,
          p: Array<out String>?,
          s: String?,
          a: Array<out String>?,
          o: String?,
        ): Cursor? = null

        override fun getType(uri: Uri) = "video/mp4"

        override fun insert(uri: Uri, v: ContentValues?): Uri? = null

        override fun delete(uri: Uri, s: String?, a: Array<out String>?) =
          if (target.delete()) 1 else 0

        override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

        override fun call(method: String, arg: String?, extras: Bundle?): Bundle =
          when (method) {
            "android:createDocument" -> {
              target.writeBytes(byteArrayOf())
              Bundle().apply { putParcelable("uri", fileUri) }
            }
            "android:deleteDocument" -> {
              target.delete()
              Bundle()
            }
            else -> error(method)
          }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
          if (mode == "r" && corrupt)
            java.io.RandomAccessFile(target, "rw").use { it.writeByte(99) }
          return ParcelFileDescriptor.open(
            target,
            if (mode == "r") ParcelFileDescriptor.MODE_READ_ONLY
            else
              ParcelFileDescriptor.MODE_READ_WRITE or
                ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE,
          )
        }
      }
    provider.attachInfo(
      context,
      android.content.pm.ProviderInfo().apply {
        authority = "vault.restore"
        applicationInfo = context.applicationInfo
        packageName = context.packageName
        exported = true
      },
    )
    ShadowContentResolver.registerProviderInternal("vault.restore", provider)
    val source =
      File(context.cacheDir, "original.mp4").apply { writeBytes(ByteArray(140000) { it.toByte() }) }
    SecureVault(context) { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
      .use { vault ->
        vault.setup("123456".toCharArray())
        val entry = vault.add(source.toUri(), source.name)
        val result = runCatching { vault.restore(entry, tree) }
        if (corrupt) {
          assertTrue(result.isFailure)
          assertFalse(target.exists())
          assertEquals(entry, vault.entries().single())
          assertArrayEquals(source.readBytes(), vault.materialize(entry).readBytes())
        } else {
          result.getOrThrow()
          assertArrayEquals(source.readBytes(), target.readBytes())
          assertTrue(vault.entries().isEmpty())
        }
      }
    source.delete()
    target.delete()
    SecureVault.clearPlaybackCache(context)
  }
}
