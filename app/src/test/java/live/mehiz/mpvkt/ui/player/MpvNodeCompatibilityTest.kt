package live.mehiz.mpvkt.ui.player

import `is`.xyz.mpv.MPVNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MpvNodeCompatibilityTest {
  @Serializable data class Track(val title: String, val externalFilename: String, val id: Long)

  @Test
  fun nativeTrackNamesAndPathsAreDecodedWithoutLosingEscapesOrUnicode() {
    val name = "العربية\nTitle \"quoted\"\\"
    val path = "C:\\Videos\\name\"part.mkv"
    val node =
      MPVNode.MapNode(
        mapOf(
          "title" to MPVNode.StringNode(name),
          "externalFilename" to MPVNode.StringNode(path),
          "id" to MPVNode.IntNode(4_294_967_297L),
        )
      )
    val result = node.toObject<Track>(Json)
    assertEquals(name, result.title)
    assertEquals(path, result.externalFilename)
    assertEquals(4_294_967_297L, result.id)
  }

  @Test
  fun nodeArraysMapsAndEmptyValuesRemainValidJson() {
    val node =
      MPVNode.MapNode(
        mapOf(
          "key\"\\\n" to
            MPVNode.ArrayNode(
              arrayOf(MPVNode.None, MPVNode.BooleanNode(true), MPVNode.DoubleNode(1.5))
            )
        )
      )
    val json = node.toJsonElement()
    assertEquals(json, Json.parseToJsonElement(json.toString()))
  }
}
