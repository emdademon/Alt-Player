package com.example

import com.example.player.SrtSubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtSubtitleParserTest {

    @Test
    fun `parses UTF-8 BOM and CRLF srt cues accurately`() {
        val sampleSrt = "\uFEFF1\r\n00:00:01,500 --> 00:00:04,250\r\n<i>Hello world!</i>\r\nSecond line\r\n\r\n" +
            "2\r\n00:01:02.100 --> 00:01:05.000\r\nNext cue"

        val cues = SrtSubtitleParser.parse(sampleSrt)
        assertEquals(2, cues.size)
        assertEquals(1500L, cues[0].startMs)
        assertEquals(4250L, cues[0].endMs)
        assertEquals("Hello world!\nSecond line", cues[0].text)
        assertEquals(62100L, cues[1].startMs)
        assertEquals(65000L, cues[1].endMs)
        assertEquals("Next cue", cues[1].text)
    }

    @Test
    fun `binary search finds active cue at exact timestamps`() {
        val cues = SrtSubtitleParser.parse(
            """
            1
            00:00:02,000 --> 00:00:05,000
            First line

            2
            00:00:08,000 --> 00:00:12,000
            Second line
            """.trimIndent()
        )

        assertEquals("", SrtSubtitleParser.findActiveCues(cues, 1500L))
        assertEquals("First line", SrtSubtitleParser.findActiveCues(cues, 2000L))
        assertEquals("First line", SrtSubtitleParser.findActiveCues(cues, 3500L))
        assertEquals("", SrtSubtitleParser.findActiveCues(cues, 6000L))
        assertEquals("Second line", SrtSubtitleParser.findActiveCues(cues, 10000L))
        assertTrue(SrtSubtitleParser.findActiveCues(cues, 15000L).isEmpty())
    }
}
