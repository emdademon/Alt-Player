package com.example.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SrtCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

data class ExternalSubtitleTrack(
    val id: String,
    val displayName: String,
    val uri: Uri,
    val cues: List<SrtCue>
)

object SrtSubtitleParser {

    private val TIME_LINE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")

    suspend fun loadFromUri(context: Context, uri: Uri): ExternalSubtitleTrack? =
        withContext(Dispatchers.IO) {
            val fileName = resolveFileName(context, uri)
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() } ?: return@withContext null
            val cues = parse(content)
            ExternalSubtitleTrack(
                id = uri.toString(),
                displayName = fileName,
                uri = uri,
                cues = cues
            )
        }

    fun parse(rawContent: String): List<SrtCue> {
        val normalized = rawContent
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()

        if (normalized.isEmpty()) return emptyList()

        val blocks = normalized.split(Regex("""\n\s*\n"""))
        val cues = ArrayList<SrtCue>(blocks.size)

        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            val timeLineIndex = lines.indexOfFirst { TIME_LINE_REGEX.containsMatchIn(it) }
            if (timeLineIndex < 0) continue

            val match = TIME_LINE_REGEX.find(lines[timeLineIndex]) ?: continue
            val startMs = toMillis(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            val endMs = toMillis(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            )

            if (endMs <= startMs) continue

            val textLines = lines.drop(timeLineIndex + 1)
                .joinToString("\n")
                .replace(HTML_TAG_REGEX, "")
                .trim()

            if (textLines.isNotEmpty()) {
                cues.add(SrtCue(startMs = startMs, endMs = endMs, text = textLines))
            }
        }

        return cues.sortedBy { it.startMs }
    }

    fun findActiveCues(cues: List<SrtCue>, positionMs: Long): String {
        if (cues.isEmpty()) return ""
        var low = 0
        var high = cues.lastIndex
        var candidateIndex = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                candidateIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        if (candidateIndex < 0) return ""

        val active = mutableListOf<String>()
        var idx = candidateIndex
        while (idx >= 0 && cues[idx].endMs >= positionMs) {
            if (cues[idx].startMs <= positionMs && positionMs <= cues[idx].endMs) {
                active.add(0, cues[idx].text)
            }
            idx--
        }
        return active.joinToString("\n")
    }

    private fun toMillis(h: String, m: String, s: String, ms: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val minutes = m.toLongOrNull() ?: 0L
        val seconds = s.toLongOrNull() ?: 0L
        val millis = ms.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Subtitle.srt"
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { name = it }
                    }
                }
            }
        }
        return name
    }
}
