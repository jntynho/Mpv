package live.mehiz.mpvkt.di

import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import live.mehiz.mpvkt.subtitles.*
import java.io.File

// generic dependencies for the app's needs
val AppModule = module {
  single { SubtitleKeys(androidContext()) }
  single { SubtitleFonts(androidContext()) }
  single { SubtitleService(SubtitleHttp(), get<SubtitleKeys>()::get, File(androidContext().filesDir, "subtitle-downloads")) }

  single {
    Json {
      isLenient = true
      ignoreUnknownKeys = true
    }
  }
}
