package live.mehiz.mpvkt.ui.secure

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.*
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** AES-GCM authenticates every file; index and names are encrypted as well. */
internal object VaultCrypto {
  private val random = SecureRandom()

  fun random(size: Int) = ByteArray(size).also(random::nextBytes)

  fun derive(pin: CharArray, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(pin, salt, 210000, 256)
    return try {
      SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }

  fun cipher(mode: Int, key: javax.crypto.SecretKey, iv: ByteArray, aad: String): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(mode, key, GCMParameterSpec(128, iv))
      updateAAD(aad.toByteArray())
    }

  fun seal(key: javax.crypto.SecretKey, data: ByteArray, aad: String): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    cipher.updateAAD(aad.toByteArray())
    return cipher.iv + cipher.doFinal(data)
  }

  fun open(key: javax.crypto.SecretKey, data: ByteArray, aad: String): ByteArray {
    require(data.size >= 28)
    return cipher(Cipher.DECRYPT_MODE, key, data.copyOfRange(0, 12), aad)
      .doFinal(data.copyOfRange(12, data.size))
  }
}

@Serializable
internal data class VaultEntry(
  val id: String,
  val name: String,
  val bytes: Long,
  val sourceUri: String = "",
  val sourceRemoved: Boolean = false,
  val mime: String = "video/*",
)

/**
 * Lives only inside the gate. Closing the gate erases its session key. API 23+ device-bound
 * wrapping.
 */
internal class SecureVault(
  private val context: Context,
  private val wrappingKey: (() -> javax.crypto.SecretKey)? = null,
) : AutoCloseable {
  private val directory = File(context.noBackupFilesDir, "secure-vault").apply { mkdirs() }
  private val config = AtomicFile(File(directory, "account"))
  private val index = AtomicFile(File(directory, "index"))
  private val recovery = AtomicFile(File(directory, "recovery"))
  private val deviceAccess = AtomicFile(File(directory, "device-access"))
  private val options = context.getSharedPreferences("secure_folder_ui", Context.MODE_PRIVATE)
  val pinLength
    get() = options.getInt("pin_length", if (configured) 6 else 4)

  val hasRecovery
    get() = recovery.baseFile.exists()

  val hasDeviceAccess
    get() = deviceAccess.baseFile.exists()

  private val attempts = context.getSharedPreferences("secure_vault_attempts", Context.MODE_PRIVATE)
  private var key: ByteArray? = null
  private val json = Json { ignoreUnknownKeys = true }
  val configured
    get() = config.baseFile.exists()

  val unlocked
    get() = key != null

  private fun deviceKey(): javax.crypto.SecretKey {
    wrappingKey?.let {
      return it()
    }
    if (android.os.Build.VERSION.SDK_INT >= 23) {
      val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val alias = "mpv-secure-vault-v1"
      (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let {
        return it
      }
      return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
          init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .build()
          )
        }
        .generateKey()
    }
    error("Secure Folder requires Android 6.0 or newer")
  }

  private fun write(file: AtomicFile, bytes: ByteArray) {
    val stream = file.startWrite()
    try {
      stream.write(bytes)
      file.finishWrite(stream)
    } catch (e: Throwable) {
      file.failWrite(stream)
      throw e
    }
  }

  private fun secret() = SecretKeySpec(key ?: error("Vault is locked"), "AES")

  fun setup(pin: CharArray) {
    require(!configured && pin.size in listOf(4, 6) && pin.all { it in '0'..'9' })
    val salt = VaultCrypto.random(32)
    val derived = VaultCrypto.derive(pin, salt)
    val dataKey = VaultCrypto.random(32)
    try {
      val wrapped = VaultCrypto.seal(SecretKeySpec(derived, "AES"), dataKey, "mpv-vault-pin-v1")
      write(config, VaultCrypto.seal(deviceKey(), salt + wrapped, "mpv-vault-device-v1"))
      key = dataKey.copyOf()
      save(emptyList())
      options.edit().putInt("pin_length", pin.size).commit()
    } catch (e: Throwable) {
      key?.fill(0)
      key = null
      config.delete()
      index.delete()
      throw e
    } finally {
      derived.fill(0)
      dataKey.fill(0)
      pin.fill('\u0000')
    }
  }

  fun unlock(pin: CharArray) {
    check(System.currentTimeMillis() >= attempts.getLong("next", 0)) { "Try again later" }
    var derived: ByteArray? = null
    try {
      val payload = VaultCrypto.open(deviceKey(), config.readFully(), "mpv-vault-device-v1")
      require(payload.size >= 60)
      derived = VaultCrypto.derive(pin, payload.copyOfRange(0, 32))
      val decoded =
        VaultCrypto.open(
          SecretKeySpec(derived, "AES"),
          payload.copyOfRange(32, payload.size),
          "mpv-vault-pin-v1",
        )
      require(decoded.size == 32)
      key = decoded
      entries() // A valid account must also authenticate the index before opening the UI.
      attempts.edit().clear().commit()
    } catch (e: Throwable) {
      close()
      val count = (attempts.getInt("failures", 0) + 1).coerceAtMost(10)
      attempts
        .edit()
        .putInt("failures", count)
        .putLong("next", System.currentTimeMillis() + (if (count >= 3) 30000L else 0L))
        .commit()
      throw e
    } finally {
      derived?.fill(0)
      pin.fill('\u0000')
    }
  }

  fun entries(): List<VaultEntry> {
    check(unlocked)
    if (!index.baseFile.exists()) return emptyList()
    require(index.baseFile.length() <= 2 * 1024 * 1024)
    val list =
      json.decodeFromString<List<VaultEntry>>(
        VaultCrypto.open(secret(), index.readFully(), "mpv-vault-index-v1").toString(Charsets.UTF_8)
      )
    require(
      list.size <= 5000 &&
        list.all { it.id.matches(Regex("[a-f0-9-]{36}")) && it.name.length <= 300 && it.bytes >= 0 }
    )
    return list
  }

  private fun save(items: List<VaultEntry>) {
    val bytes = json.encodeToString(items).toByteArray()
    require(bytes.size <= 2 * 1024 * 1024 - 28)
    write(index, VaultCrypto.seal(secret(), bytes, "mpv-vault-index-v1"))
  }

  suspend fun add(uri: Uri, name: String): VaultEntry {
    val current = entries()
    require(current.size < 5000)
    val id = UUID.randomUUID().toString()
    val target = File(directory, id)
    val temporary = File(directory, "$id.tmp")
    var bytes = 0L
    try {
      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(temporary).use { raw ->
          val output = DataOutputStream(raw)
          output.write("MPVVAULT1".toByteArray())
          var chunks = 0
          val buffer = ByteArray(64 * 1024)
          while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            if (n == 0) continue
            output.writeInt(n)
            output.write(VaultCrypto.seal(secret(), buffer.copyOf(n), "$id:$chunks:$n"))
            bytes += n
            chunks++
          }
          output.writeInt(0)
          output.write(VaultCrypto.seal(secret(), byteArrayOf(), "$id:end:$bytes:$chunks"))
          output.flush()
          raw.fd.sync()
        }
      } ?: error("Cannot read selected media")
      currentCoroutineContext().ensureActive()
      check(temporary.renameTo(target))
      val entry =
        VaultEntry(
          id,
          name.take(300),
          bytes,
          uri.toString(),
          false,
          runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "video/*",
        )
      save(current + entry)
      return entry
    } catch (e: Throwable) {
      temporary.delete()
      target.delete()
      throw e
    }
  }

  fun markOriginalRemoved(id: String) {
    save(entries().map { if (it.id == id) it.copy(sourceRemoved = true) else it })
  }

  fun delete(entry: VaultEntry) {
    val current = entries()
    require(entry in current)
    // Commit the manifest first; a crash can leave only an inaccessible encrypted orphan.
    save(current - entry)
    File(directory, entry.id).delete()
  }

  suspend fun materialize(entry: VaultEntry): File {
    require(entry in entries())
    val cache = File(context.cacheDir, "secure-playback").apply { mkdirs() }
    val out = File(cache, UUID.randomUUID().toString() + ".media")
    try {
      DataInputStream(File(directory, entry.id).inputStream().buffered()).use { input ->
        val header = ByteArray(9)
        input.readFully(header)
        require(header.contentEquals("MPVVAULT1".toByteArray()))
        var bytes = 0L
        var chunks = 0
        out.outputStream().use { output ->
          while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.readInt()
            require(n in 0..64 * 1024)
            val encrypted = ByteArray(n + 28)
            input.readFully(encrypted)
            if (n == 0) {
              require(
                VaultCrypto.open(secret(), encrypted, "${entry.id}:end:$bytes:$chunks").isEmpty()
              )
              require(bytes == entry.bytes && input.read() == -1)
              break
            }
            require(bytes + n <= entry.bytes)
            output.write(VaultCrypto.open(secret(), encrypted, "${entry.id}:$chunks:$n"))
            bytes += n
            chunks++
          }
        }
      }
      return out
    } catch (e: Throwable) {
      out.delete()
      throw e
    }
  }

  fun setupAccount(pin: CharArray, question: String, answer: CharArray) {
    check(!configured)
    try {
      setup(pin)
      setRecovery(question, answer)
    } catch (e: Throwable) {
      close()
      config.delete()
      index.delete()
      recovery.delete()
      throw e
    }
  }

  fun changePin(pin: CharArray) {
    require(unlocked && pin.size in listOf(4, 6) && pin.all { it in '0'..'9' })
    val salt = VaultCrypto.random(32)
    val derived = VaultCrypto.derive(pin, salt)
    try {
      val wrapped = VaultCrypto.seal(SecretKeySpec(derived, "AES"), key!!, "mpv-vault-pin-v1")
      write(config, VaultCrypto.seal(deviceKey(), salt + wrapped, "mpv-vault-device-v1"))
      options.edit().putInt("pin_length", pin.size).commit()
    } finally {
      derived.fill(0)
      pin.fill('\u0000')
    }
  }

  private fun normalized(answer: CharArray): CharArray =
    java.text.Normalizer.normalize(String(answer), java.text.Normalizer.Form.NFKC)
      .trim()
      .lowercase(java.util.Locale.ROOT)
      .replace(Regex("\\s+"), " ")
      .toCharArray()

  fun setRecovery(question: String, answer: CharArray) {
    require(unlocked && question.trim().length in 3..200 && answer.size in 3..256)
    val clean = normalized(answer)
    require(clean.size >= 3)
    val salt = VaultCrypto.random(32)
    val derived = VaultCrypto.derive(clean, salt)
    try {
      val output = ByteArrayOutputStream()
      DataOutputStream(output).use { data ->
        data.writeUTF(question.trim())
        data.write(salt)
        data.write(VaultCrypto.seal(SecretKeySpec(derived, "AES"), key!!, "mpv-vault-answer-v1"))
      }
      write(recovery, VaultCrypto.seal(deviceKey(), output.toByteArray(), "mpv-vault-recovery-v1"))
    } finally {
      derived.fill(0)
      answer.fill('\u0000')
      clean.fill('\u0000')
    }
  }

  private fun recoveryData(): ByteArray {
    require(recovery.baseFile.length() in 28..8192)
    return VaultCrypto.open(deviceKey(), recovery.readFully(), "mpv-vault-recovery-v1")
  }

  fun recoveryQuestion(): String =
    DataInputStream(recoveryData().inputStream()).use { it.readUTF() }

  fun recoverPin(answer: CharArray, newPin: CharArray) {
    check(System.currentTimeMillis() >= attempts.getLong("next", 0))
    val clean = normalized(answer)
    var derived: ByteArray? = null
    try {
      DataInputStream(recoveryData().inputStream()).use { data ->
        data.readUTF()
        val salt = ByteArray(32)
        data.readFully(salt)
        derived = VaultCrypto.derive(clean, salt)
        val decoded =
          VaultCrypto.open(SecretKeySpec(derived, "AES"), data.readBytes(), "mpv-vault-answer-v1")
        require(decoded.size == 32)
        key = decoded
        entries()
        changePin(newPin)
      }
      attempts.edit().clear().commit()
    } catch (e: Throwable) {
      close()
      val count = (attempts.getInt("failures", 0) + 1).coerceAtMost(10)
      attempts
        .edit()
        .putInt("failures", count)
        .putLong("next", System.currentTimeMillis() + if (count >= 3) 30000L else 0L)
        .commit()
      throw e
    } finally {
      derived?.fill(0)
      answer.fill('\u0000')
      clean.fill('\u0000')
      newPin.fill('\u0000')
    }
  }

  /** This key is usable only after Android authenticates the device owner. */
  private fun authenticatedKey(): javax.crypto.SecretKey {
    if (android.os.Build.VERSION.SDK_INT >= 23) {
      val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val alias = "mpv-secure-vault-device-auth-v1"
      (store.getKey(alias, null) as? javax.crypto.SecretKey)?.let {
        return it
      }
      return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply {
          init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setKeySize(256)
              .setUserAuthenticationRequired(true)
              .setUserAuthenticationValidityDurationSeconds(30)
              .build()
          )
        }
        .generateKey()
    }
    error("Device authentication requires Android 6.0")
  }

  fun enableDeviceAccess() {
    check(unlocked)
    write(deviceAccess, VaultCrypto.seal(authenticatedKey(), key!!, "mpv-vault-device-access-v1"))
  }

  fun disableDeviceAccess() {
    deviceAccess.delete()
    runCatching {
      KeyStore.getInstance("AndroidKeyStore")
        .apply { load(null) }
        .deleteEntry("mpv-secure-vault-device-auth-v1")
    }
  }

  fun unlockWithDevice() {
    try {
      val decoded =
        VaultCrypto.open(authenticatedKey(), deviceAccess.readFully(), "mpv-vault-device-access-v1")
      require(decoded.size == 32)
      key = decoded
      entries()
    } catch (e: Throwable) {
      close()
      throw e
    }
  }

  suspend fun moveOriginal(entry: VaultEntry): Boolean {
    require(entry in entries())
    currentCoroutineContext().ensureActive()
    if (entry.sourceUri.isBlank()) return false
    val uri = Uri.parse(entry.sourceUri)
    if (uri.scheme != "content") return false
    val removed =
      runCatching {
          if (android.provider.DocumentsContract.isDocumentUri(context, uri))
            android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri)
          else context.contentResolver.delete(uri, null, null) > 0
        }
        .getOrDefault(false)
    if (removed) markOriginalRemoved(entry.id)
    return removed
  }

  /**
   * Restore into a user-selected directory; delete the encrypted copy only after read-back
   * verification.
   */
  suspend fun restore(entry: VaultEntry, tree: Uri) {
    val target =
      androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree)
        ?: error("Folder unavailable")
    val clear = materialize(entry)
    var created: androidx.documentfile.provider.DocumentFile? = null
    try {
      created =
        target.createFile(
          if ("*" in entry.mime)
            android.webkit.MimeTypeMap.getSingleton()
              .getMimeTypeFromExtension(
                entry.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
              ) ?: "application/octet-stream"
          else entry.mime,
          entry.name.replace('/', '_').replace('\\', '_'),
        ) ?: error("Cannot create restored file")
      context.contentResolver.openOutputStream(created.uri, "wt")?.use { out ->
        clear.inputStream().use { input ->
          val buffer = ByteArray(65536)
          while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
          }
        }
        out.flush()
        if (out is FileOutputStream) runCatching { out.fd.sync() }
      } ?: error("Cannot restore")
      fun digest(input: InputStream): ByteArray =
        input.use { stream ->
          val hash = java.security.MessageDigest.getInstance("SHA-256")
          val buffer = ByteArray(65536)
          while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            hash.update(buffer, 0, n)
          }
          hash.digest()
        }
      val readBack =
        context.contentResolver.openInputStream(created.uri) ?: error("Cannot verify restored file")
      check(digest(clear.inputStream()).contentEquals(digest(readBack)))
      currentCoroutineContext().ensureActive()
      delete(entry)
    } catch (e: Throwable) {
      created?.delete()
      throw e
    } finally {
      clear.delete()
    }
  }

  override fun close() {
    key?.fill(0)
    key = null
  }

  companion object {
    fun clearPlaybackCache(context: Context) {
      File(context.cacheDir, "secure-playback").deleteRecursively()
    }
  }
}
