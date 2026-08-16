package com.notesheet.app

enum class Field { BED, NAME, CONSULTANT, DETAILS }

data class ParsedFields(
    var bedNumber: String = "",
    var patientName: String = "",
    var primaryConsultant: String = "",
    var details: String = ""
)

/**
 * Turns free-form dictation into structured fields, no matter what order the
 * user says them in. The user speaks a "marker" word/phrase (e.g. "bed number",
 * "patient name", "primary consultant" / "consultant" / "doctor", "details")
 * and everything spoken after that marker - up until the next marker - is
 * assigned to that field. If the user only ever says "details" and keeps
 * talking, every following word stays in the Details field, exactly as
 * requested. Anything spoken before the very first marker is treated as
 * Details as a safe fallback so nothing spoken is ever lost.
 */
object VoiceParser {

    // Ordered longest-first so "primary consultant" is matched before "consultant".
    private val markers: List<Pair<String, Field>> = listOf(
        "primary consultant" to Field.CONSULTANT,
        "patient name" to Field.NAME,
        "bed number" to Field.BED,
        "bed no" to Field.BED,
        "consultant" to Field.CONSULTANT,
        "doctor" to Field.CONSULTANT,
        "patient" to Field.NAME,
        "details" to Field.DETAILS,
        "detail" to Field.DETAILS,
        "name" to Field.NAME,
        "bed" to Field.BED
    )

    private data class Occurrence(val start: Int, val end: Int, val field: Field)

    fun parse(rawText: String, existing: ParsedFields = ParsedFields()): ParsedFields {
        val text = rawText.trim()
        if (text.isEmpty()) return existing
        val lower = text.lowercase()

        val occurrences = mutableListOf<Occurrence>()
        var i = 0
        while (i < lower.length) {
            var matchedLen = 0
            var matchedField: Field? = null
            for ((marker, field) in markers) {
                if (lower.startsWith(marker, i)) {
                    val before = i == 0 || !lower[i - 1].isLetter()
                    val afterIdx = i + marker.length
                    val after = afterIdx >= lower.length || !lower[afterIdx].isLetter()
                    if (before && after) {
                        matchedLen = marker.length
                        matchedField = field
                        break
                    }
                }
            }
            if (matchedField != null) {
                occurrences.add(Occurrence(i, i + matchedLen, matchedField))
                i += matchedLen
            } else {
                i++
            }
        }

        val result = existing.copy()

        fun append(field: Field, chunk: String) {
            val cleaned = chunk.trim().trim(',', '.', ';', ':', '-')
            if (cleaned.isEmpty()) return
            when (field) {
                Field.BED -> result.bedNumber = joinNonEmpty(result.bedNumber, cleaned)
                Field.NAME -> result.patientName = joinNonEmpty(result.patientName, cleaned)
                Field.CONSULTANT -> result.primaryConsultant =
                    joinNonEmpty(result.primaryConsultant, DoctorDictionary.correct(cleaned))
                Field.DETAILS -> result.details =
                    joinNonEmpty(result.details, MedicalDictionary.correct(cleaned))
            }
        }

        if (occurrences.isEmpty()) {
            // Nothing recognized as a marker at all - keep it safe in Details.
            append(Field.DETAILS, text)
            return result
        }

        // Leading text before the first marker.
        if (occurrences.first().start > 0) {
            val lead = text.substring(0, occurrences.first().start)
            if (lead.isNotBlank()) append(Field.DETAILS, lead)
        }

        for ((idx, occ) in occurrences.withIndex()) {
            val chunkEnd = if (idx + 1 < occurrences.size) occurrences[idx + 1].start else text.length
            val chunk = text.substring(occ.end, chunkEnd)
            append(occ.field, chunk)
        }

        return result
    }

    private fun joinNonEmpty(a: String, b: String): String =
        if (a.isBlank()) b else "$a $b"
}
