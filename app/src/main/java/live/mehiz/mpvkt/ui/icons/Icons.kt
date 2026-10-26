package live.mehiz.mpvkt.ui.icons
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.dp
object Icons {
 object Filled
 object Outlined
 object Rounded
 val Default get() = Filled
 object AutoMirrored {
  object Filled
  object Outlined
  val Default get() = Filled
 }
}
internal fun tabler(name: String, mirrored: Boolean, vararg paths: String): ImageVector =
 ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirrored).apply {
  paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), stroke = SolidColor(Color.Black), strokeLineWidth = 1.65f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) }
 }.build()
