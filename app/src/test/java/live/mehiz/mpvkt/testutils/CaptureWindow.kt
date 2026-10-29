package live.mehiz.mpvkt.testutils

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import java.io.File

/** Native Compose renders including app dialogs/popups, with deterministic test data only. */
internal fun captureWindow(activity: Activity, name: String) {
  val view = activity.findViewById<View>(android.R.id.content)
  val node = android.graphics.RenderNode("refinement-ui")
  node.setPosition(0, 0, view.width, view.height)
  val canvas = node.beginRecording(view.width, view.height)
  android.view.inspector.WindowInspector.getGlobalWindowViews()
    .filter { it.isShown }
    .forEach { window ->
      val location = IntArray(2)
      window.getLocationOnScreen(location)
      val save = canvas.save()
      canvas.translate(location[0].toFloat(), location[1].toFloat())
      window.draw(canvas)
      canvas.restoreToCount(save)
    }
  node.endRecording()
  val method =
    android.graphics.HardwareRenderer::class
      .java
      .getDeclaredMethod(
        "createHardwareBitmap",
        android.graphics.RenderNode::class.java,
        Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType,
      )
      .apply { isAccessible = true }
  val hardware = method.invoke(null, node, view.width, view.height) as Bitmap
  val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
  hardware.recycle()
  node.discardDisplayList()
  File("build/reports/refinement/$name.png")
    .apply { parentFile?.mkdirs() }
    .outputStream()
    .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  bitmap.recycle()
}
