package com.notesheet.app

/**
 * Corrects speech-to-text output for consultant names to the exact spelling used
 * on record, regardless of whether the recognizer heard "doctor" / "dr" / nothing at all.
 */
object DoctorDictionary {

    val doctors: List<String> = listOf(
        "Dr. Yatin Sagwekar",
        "Dr. Chaitanya",
        "Dr. Bharat",
        "Dr. Poonam",
        "Dr. Sonali Gautam",
        "Dr. Dipak Bhangale"
    )

    fun correct(input: String): String {
        if (input.isBlank()) return input
        var result = input
        for (doc in doctors) {
            val surnamePart = doc.removePrefix("Dr. ")
            // Match "dr sagwekar", "doctor yatin sagwekar", "yatin sagwekar", etc.
            val pattern = "(?i)\\b(dr\\.?|doctor)?\\s*" + Regex.escape(surnamePart)
            val regex = Regex(pattern)
            result = regex.replace(result, doc)
        }
        return result
    }
}
