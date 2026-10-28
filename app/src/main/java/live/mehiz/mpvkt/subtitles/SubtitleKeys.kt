package live.mehiz.mpvkt.subtitles

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/** Called from IO only. No provider keys are stored in ordinary preferences or backup. */
internal class SubtitleKeys(context: Context) {
  private val app = context.applicationContext
  private val storage = app.getSharedPreferences("subtitle_credentials", Context.MODE_PRIVATE)
  private val alias = "mpv-subtitle-credentials-v1"

  @Synchronized
  private fun key(): SecretKey {
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    if (Build.VERSION.SDK_INT >= 23) {
      if (!store.containsAlias(alias))
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
          init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
              )
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build()
          )
          generateKey()
        }
      return store.getKey(alias, null) as SecretKey
    }
    @Suppress("DEPRECATION")
    if (!store.containsAlias(alias)) {
      check(!storage.contains("wrapped")) { "Credential protection key is unavailable" }
      val end = Calendar.getInstance().apply { add(Calendar.YEAR, 25) }
      val spec =
        KeyPairGeneratorSpec.Builder(app)
          .setAlias(alias)
          .setSubject(X500Principal("CN=MPV subtitle keys"))
          .setSerialNumber(BigInteger.ONE)
          .setStartDate(Calendar.getInstance().time)
          .setEndDate(end.time)
          .build()
      KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply {
        initialize(spec)
        generateKeyPair()
      }
    }
    val wrapper = Cipher.getInstance("RSA/ECB/PKCS1Padding")
    val wrapped = storage.getString("wrapped", null)
    if (wrapped != null) {
      wrapper.init(Cipher.DECRYPT_MODE, store.getKey(alias, null))
      return SecretKeySpec(wrapper.doFinal(Base64.decode(wrapped, Base64.NO_WRAP)), "AES")
    }
    val secret = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    wrapper.init(Cipher.ENCRYPT_MODE, store.getCertificate(alias).publicKey)
    check(
      storage
        .edit()
        .putString(
          "wrapped",
          Base64.encodeToString(wrapper.doFinal(secret.encoded), Base64.NO_WRAP),
        )
        .commit()
    )
    return secret
  }

  @Synchronized
  fun get(provider: String): String {
    val encoded = storage.getString(provider, null) ?: return ""
    val pieces = encoded.split(':')
    check(pieces.size == 2) { "Invalid stored credential" }
    return Cipher.getInstance("AES/GCM/NoPadding").run {
      init(
        Cipher.DECRYPT_MODE,
        key(),
        GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)),
      )
      String(doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }
  }

  @Synchronized
  fun put(provider: String, value: String) {
    require(provider in SubtitleCatalog.keys)
    require(value.length <= 4096 && value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
      "Invalid API key"
    }
    if (value.isBlank()) {
      check(storage.edit().remove(provider).commit())
      return
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
    check(
      storage
        .edit()
        .putString(
          provider,
          Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
        .commit()
    )
  }
}
