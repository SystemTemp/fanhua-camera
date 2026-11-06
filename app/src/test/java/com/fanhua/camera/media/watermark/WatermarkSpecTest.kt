package com.fanhua.camera.media.watermark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 水印规格解析测试。
 *
 * 用最小化的 JSON 片段验证解析逻辑，不依赖 Android 运行时。
 */
class WatermarkSpecTest {

    private fun repo(json: String) = WatermarkRepository { json }

    private val minimalJson = """
    {
      "groups": {
        "leica": { "name": "徕卡", "priority": 1 },
        "film":  { "name": "胶片", "priority": 3 }
      },
      "watermarks": {
        "leica_92": {
          "id": "leica_92",
          "category": "leica",
          "nativeId": "92",
          "names": { "zh": "徕卡 92 号", "zhTW": "徠卡 92 號", "en": "Leica 92" },
          "renderMode": "frame",
          "priority": 0.54,
          "frame": {
            "mode": "frame",
            "paddingH": 80.0,
            "topH": 188.0,
            "bottomParts": [ { "id": "p1", "h": 100.0 }, { "id": "p2", "h": 94.0 } ]
          },
          "themes": {
            "white": {
              "name": "white",
              "color": "#FFFFFFFF",
              "foreground": ["#FF000000", "#4D000000", "#80000000", "black"]
            }
          },
          "defaultTheme": "white",
          "fonts": {
            "f1": {
              "size": 21.6,
              "colorByTheme": { "white": "#FF000000" },
              "fontFamily": "default",
              "letterSpacing": 0.0
            }
          },
          "slots": {
            "layout": [
              {
                "type": "Layout",
                "id": "all_content",
                "width": { "kind": "wrap", "value": null },
                "height": { "kind": "wrap", "value": null },
                "orientation": "vertical",
                "gravity": ["center"],
                "children": [
                  {
                    "type": "TextView",
                    "id": "model",
                    "width": { "kind": "wrap", "value": null },
                    "height": { "kind": "wrap", "value": null },
                    "fontRef": "f1",
                    "textParsed": { "kind": "model", "template": "@model= @{series}@{versionNumber}", "fields": [] }
                  }
                ]
              }
            ]
          }
        },
        "film_12": {
          "id": "film_12",
          "category": "film",
          "nativeId": "12",
          "names": { "zh": "胶片 12" },
          "renderMode": "overlay",
          "priority": 2,
          "themes": {},
          "fonts": {},
          "slots": {}
        }
      }
    }
    """.trimIndent()

    @Test
    fun `解析分类并按优先级排序`() {
        val groups = repo(minimalJson).groups()
        assertEquals(2, groups.size)
        assertEquals("leica", groups[0].first)
        assertEquals("film", groups[1].first)
    }

    @Test
    fun `解析水印基本字段`() {
        val spec = repo(minimalJson).spec("leica_92")
        assertNotNull(spec)
        assertEquals("leica_92", spec!!.id)
        assertEquals("leica", spec.category)
        assertEquals("92", spec.nativeId)
        assertEquals(RenderMode.FRAME, spec.renderMode)
        assertEquals(0.54f, spec.priority, 0.001f)
    }

    @Test
    fun `解析多语言名称`() {
        val spec = repo(minimalJson).spec("leica_92")!!
        assertEquals("徕卡 92 号", spec.names["zh"])
        assertEquals("Leica 92", spec.names["en"])
        // 语言回退
        assertEquals("徕卡 92 号", spec.displayName("zh-CN"))
        assertEquals("徠卡 92 號", spec.displayName("zh-TW"))
        assertEquals("Leica 92", spec.displayName("en-US"))
    }

    @Test
    fun `解析画框参数`() {
        val frame = repo(minimalJson).spec("leica_92")!!.frame
        assertNotNull(frame)
        assertEquals(80f, frame!!.paddingH, 0.01f)
        assertEquals(188f, frame.topH, 0.01f)
        assertEquals(2, frame.bottomParts.size)
        assertEquals(194f, frame.bottomTotal, 0.01f)
    }

    @Test
    fun `解析主题与前景色`() {
        val theme = repo(minimalJson).spec("leica_92")!!.themes["white"]
        assertNotNull(theme)
        assertEquals("#FFFFFFFF", theme!!.color)
        assertEquals(4, theme.foreground.size)
        assertEquals("#FF000000", theme.foregroundAt(0))
        assertEquals("black", theme.foregroundAt(3))
        // 越界回退到主色
        assertEquals("#FF000000", theme.foregroundAt(99))
    }

    @Test
    fun `解析字体定义`() {
        val font = repo(minimalJson).spec("leica_92")!!.fonts["f1"]
        assertNotNull(font)
        assertEquals(21.6f, font!!.size, 0.01f)
        assertEquals("default", font.fontFamily)
        // 按主题取色
        assertEquals("#FF000000", font.colorIn("white", "#FFFFFFFF"))
    }

    @Test
    fun `解析布局树`() {
        val slots = repo(minimalJson).spec("leica_92")!!.slots
        val layout = slots["layout"]
        assertNotNull(layout)
        assertEquals(1, layout!!.size)
        val root = layout[0]
        assertEquals(NodeType.LAYOUT, root.type)
        assertEquals("all_content", root.id)
        assertEquals("vertical", root.orientation)
        assertEquals(1, root.children.size)
        val text = root.children[0]
        assertEquals(NodeType.TEXT, text.type)
        assertEquals("model", text.id)
        assertEquals("f1", text.fontRef)
        assertNotNull(text.textParsed)
        assertEquals(TextKind.MODEL, text.textParsed!!.kind)
    }

    @Test
    fun `空主题的水印能正常解析`() {
        val spec = repo(minimalJson).spec("film_12")
        assertNotNull(spec)
        assertTrue(spec!!.themes.isEmpty())
        assertEquals(RenderMode.OVERLAY, spec.renderMode)
    }

    @Test
    fun `不存在的水印返回 null`() {
        assertNull(repo(minimalJson).spec("not_exist"))
    }

    @Test
    fun `按分类列出水印`() {
        val r = repo(minimalJson)
        assertEquals(listOf("leica_92"), r.idsInGroup("leica"))
        assertEquals(listOf("film_12"), r.idsInGroup("film"))
        assertTrue(r.idsInGroup("disney").isEmpty())
    }

    @Test
    fun `全部水印 id 按分类顺序返回`() {
        val all = repo(minimalJson).allIds()
        assertEquals(2, all.size)
        assertEquals("leica_92", all[0])
        assertEquals("film_12", all[1])
    }

    @Test
    fun `renderMode 解析覆盖全部取值`() {
        assertEquals(RenderMode.FRAME, RenderMode.from("frame"))
        assertEquals(RenderMode.SIDE, RenderMode.from("side"))
        assertEquals(RenderMode.OVERLAY, RenderMode.from("overlay"))
        assertEquals(RenderMode.BORDER, RenderMode.from("border"))
        assertEquals(RenderMode.FILMSTRIP, RenderMode.from("filmstrip"))
        // 未知值回退 overlay
        assertEquals(RenderMode.OVERLAY, RenderMode.from("unknown"))
        assertEquals(RenderMode.OVERLAY, RenderMode.from(null))
    }

    @Test
    fun `尺寸类型解析`() {
        assertEquals(DimKind.WRAP, Dim.WRAP.kind)
        assertEquals(DimKind.MATCH, Dim.MATCH.kind)
        val dp = Dim.dp(24f)
        assertEquals(DimKind.DP, dp.kind)
        assertEquals(24f, dp.value!!, 0.01f)
        val pct = Dim.pct(50f)
        assertEquals(DimKind.PERCENT, pct.kind)
        assertEquals(50f, pct.value!!, 0.01f)
    }
}
