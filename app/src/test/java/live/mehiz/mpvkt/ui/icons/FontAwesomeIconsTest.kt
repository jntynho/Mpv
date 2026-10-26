package live.mehiz.mpvkt.ui.icons
import live.mehiz.mpvkt.ui.icons.filled.*
import live.mehiz.mpvkt.ui.icons.automirrored.filled.ArrowBack
import org.junit.Assert.assertTrue
import org.junit.Test
class FontAwesomeIconsTest {
 @Test fun allBundledIconsHaveValidGeometry() {
  val icons = listOf(Icons.Default.Gpu, Icons.Default.Shadow, Icons.Default.Sort, Icons.Default.Add, Icons.Default.AddCircle, Icons.Default.AlignVerticalCenter, Icons.Default.ArrowBack, Icons.Default.ArrowDropDown, Icons.Default.ArrowDropUp, Icons.Default.AspectRatio, Icons.Default.Audiotrack, Icons.Default.Bookmarks, Icons.Default.BorderColor, Icons.Default.BorderStyle, Icons.Default.BrightnessHigh, Icons.Default.BrightnessLow, Icons.Default.BrightnessMedium, Icons.Default.BugReport, Icons.Default.CatchingPokemon, Icons.Default.Clear, Icons.Default.Close, Icons.Default.Code, Icons.Default.ContentCopy, Icons.Default.Delete, Icons.Default.DoubleArrow, Icons.Default.Edit, Icons.Default.EditOff, Icons.Default.Folder, Icons.Default.FormatAlignCenter, Icons.Default.FormatAlignJustify, Icons.Default.FormatAlignLeft, Icons.Default.FormatAlignRight, Icons.Default.FormatBold, Icons.Default.FormatClear, Icons.Default.FormatColorFill, Icons.Default.FormatColorReset, Icons.Default.FormatColorText, Icons.Default.FormatItalic, Icons.Default.FormatSize, Icons.Default.Gesture, Icons.Default.Gradient, Icons.Default.Headset, Icons.Default.HeadsetOff, Icons.Default.HelpOutline, Icons.Default.Image, Icons.Default.Info, Icons.Default.InsertDriveFile, Icons.Default.KeyboardAlt, Icons.Default.Link, Icons.Default.Lock, Icons.Default.LockOpen, Icons.Default.Memory, Icons.Default.MoreTime, Icons.Default.MoreVert, Icons.Default.Movie, Icons.Default.NotInterested, Icons.Default.Palette, Icons.Default.PictureInPictureAlt, Icons.Default.PlayCircle, Icons.Default.Refresh, Icons.Default.RemoveCircle, Icons.Default.RestartAlt, Icons.Default.Schedule, Icons.Default.ScreenRotation, Icons.Default.Search, Icons.Default.Settings, Icons.Default.Star, Icons.Default.StarOutline, Icons.Default.Subtitles, Icons.Default.Terminal, Icons.Default.Timer, Icons.Default.Tune, Icons.Default.VolumeDown, Icons.Default.VolumeMute, Icons.Default.VolumeOff, Icons.Default.VolumeUp, Icons.Default.Play, Icons.Default.Pause)
  icons.forEach { assertTrue(it.name, it.root.size > 0) }
 }
 @Test fun backArrowMirrorsForArabic() {
  assertTrue(Icons.AutoMirrored.Filled.ArrowBack.autoMirror)
 }
}
