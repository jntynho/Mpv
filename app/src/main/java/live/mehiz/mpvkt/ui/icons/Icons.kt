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
internal fun fontAwesome(name: String, mirrored: Boolean, width: Int, height: Int, vararg paths: String): ImageVector =
 ImageVector.Builder(name, (24f * width / height).dp, 24.dp, width.toFloat(), height.toFloat(), autoMirror = mirrored).apply {
  paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) }
 }.build()
