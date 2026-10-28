package live.mehiz.mpvkt.subtitles

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.*

internal interface SubtitleTransport {
  suspend fun get(
    url: String,
    headers: Map<String, String> = emptyMap(),
    form: Map<String, String>? = null,
    limit: Int = 3 * 1024 * 1024,
  ): ByteArray
}

internal fun endpoint(base: String, parameters: Map<String, Any?>): String {
  val args =
    parameters
      .filterValues { it != null && it.toString().isNotBlank() }
      .entries
      .joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value.toString(), "UTF-8")
      }
  return base + if (args.isEmpty()) "" else (if ('?' in base) "&" else "?") + args
}

/** Only HTTPS public hosts; credentials are never forwarded across redirects. */
internal fun validatedSubtitleUri(url: String): URI {
  val uri = URI(url)
  val host = uri.host?.lowercase() ?: throw IOException("Invalid subtitle URL")
  if (
    uri.scheme != "https" ||
      uri.userInfo != null ||
      uri.port !in listOf(-1, 443) ||
      host == "localhost" ||
      host.endsWith(".local") ||
      host.endsWith(".internal") ||
      !host.contains('.') ||
      host.matches(Regex("[0-9.]+")) ||
      ':' in host
  ) {
    throw IOException("Unsupported subtitle address")
  }
  return uri
}

internal class SubtitleHttp : SubtitleTransport {
  override suspend fun get(
    url: String,
    headers: Map<String, String>,
    form: Map<String, String>?,
    limit: Int,
  ): ByteArray = coroutineScope {
    var current = url
    var requestHeaders = headers
    var requestForm = form
    repeat(5) {
      currentCoroutineContext().ensureActive()
      val uri = validatedSubtitleUri(current)
      val connection =
        (uri.toURL().openConnection() as HttpURLConnection).apply {
          connectTimeout = 12_000
          readTimeout = 20_000
          instanceFollowRedirects = false
          setRequestProperty("User-Agent", "MPV/0.12 (Android; subtitle search)")
          setRequestProperty("Accept-Encoding", "gzip")
          requestHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
        }
      val cancel =
        launch(start = CoroutineStart.UNDISPATCHED, context = Dispatchers.IO) {
          try {
            awaitCancellation()
          } finally {
            connection.disconnect()
          }
        }
      try {
        val result =
          withContext(Dispatchers.IO) {
            check(
              InetAddress.getAllByName(uri.host).none {
                it.isAnyLocalAddress ||
                  it.isLoopbackAddress ||
                  it.isLinkLocalAddress ||
                  it.isSiteLocalAddress ||
                  it.isMulticastAddress
              }
            ) {
              "Private subtitle host is not allowed"
            }
            requestForm?.let { values ->
              connection.requestMethod = "POST"
              connection.doOutput = true
              connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
              connection.outputStream.use {
                it.write(endpoint("", values).removePrefix("?").toByteArray())
              }
            }
            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) return@withContext null
            if (status == 400 && uri.host == "sub.wyzie.io" && uri.path == "/search") {
              val error =
                connection.errorStream
                  ?.use { stream ->
                    val buffer = ByteArray(16384)
                    val count = stream.read(buffer)
                    if (count > 0) String(buffer, 0, count) else ""
                  }
                  .orEmpty()
              if (error.contains("No subtitles found", true)) return@withContext "[]".toByteArray()
            }
            if (status !in 200..299) throw IOException("HTTP $status from ${uri.host}")
            if (connection.contentLength > limit)
              throw IOException("Subtitle response is too large")
            val raw = connection.inputStream
            val input =
              if (connection.contentEncoding.equals("gzip", true)) GZIPInputStream(raw) else raw
            input.use {
              val output = ByteArrayOutputStream()
              val buffer = ByteArray(8192)
              while (true) {
                currentCoroutineContext().ensureActive()
                val count = it.read(buffer)
                if (count < 0) break
                if (output.size() + count > limit)
                  throw IOException("Subtitle response is too large")
                output.write(buffer, 0, count)
              }
              output.toByteArray()
            }
          }
        if (result != null) return@coroutineScope result
        val location =
          connection.getHeaderField("Location") ?: throw IOException("Missing redirect address")
        val next = validatedSubtitleUri(uri.resolve(location).toString())
        if (!next.host.equals(uri.host, true)) requestHeaders = emptyMap()
        if (connection.responseCode in setOf(301, 302, 303)) requestForm = null
        current = next.toString()
      } finally {
        cancel.cancel()
        connection.disconnect()
      }
    }
    throw IOException("Too many subtitle redirects")
  }
}
