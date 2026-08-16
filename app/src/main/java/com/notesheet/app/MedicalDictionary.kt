package com.notesheet.app

/**
 * Corrects speech-to-text output against a known list of clinical terms so that
 * commonly mis-heard words come out spelled the way the department actually uses them.
 * Multi-word terms are matched as whole phrases first (case-insensitive, word-boundary safe),
 * then any remaining single tricky words are corrected with a small edit-distance check.
 */
object MedicalDictionary {

    // Canonical spelling exactly as the department wants it recorded.
    val terms: List<String> = listOf(
        "OGD Scopy", "Colonoscopy", "MRCP", "ERCP", "EUS Guided FNB", "EUS Guided",
        "CECT Abdomen", "CT Abdomen", "LAP", "Laparoscopic", "Hemorrhoidectomy",
        "Fissurectomy", "COLOprep", "PAC", "Cardiac", "Endocrinologist", "Surgeon",
        "Abdominal Pain", "Constipation", "Motion", "Flatus", "Loose Stools", "Fever",
        "MRI", "Clearance Not Come", "Acute", "Chronic", "Liver Failure", "Secondary",
        "Hepatitis A", "Hepatitis B", "Hepatitis C", "Deranged", "Resolving", "Informed",
        "NBM", "NS", "RL", "Trace", "Blood Culture", "Reports", "Pancreatitis",
        "Appendicitis", "Cholecystectomy", "Cholecystitis", "Cholelithiasis", "Liver",
        "Gall Bladder", "Intestine", "Caecal", "Anus", "Oesophagus"
    )

    fun correct(input: String): String {
        if (input.isBlank()) return input
        var result = input

        // 1. Whole-phrase replacement, longest phrases first so "EUS Guided FNB"
        //    is not partially swallowed by "EUS Guided".
        for (term in terms.sortedByDescending { it.length }) {
            val regex = Regex("(?i)\\b" + Regex.escape(term) + "\\b")
            result = regex.replace(result, term)
        }

        // 2. Word-level fuzzy pass for single tricky words the recognizer mangled.
        val singleWordTerms = terms.filter { !it.contains(" ") }
        val words = result.split(" ").toMutableList()
        for (idx in words.indices) {
            val raw = words[idx]
            val cleaned = raw.trim(',', '.', ';', ':')
            if (cleaned.length < 4) continue
            // already an exact canonical term - skip
            if (singleWordTerms.any { it.equals(cleaned, ignoreCase = true) }) continue

            var best: String? = null
            var bestDist = Int.MAX_VALUE
            for (term in singleWordTerms) {
                val d = levenshtein(cleaned.lowercase(), term.lowercase())
                if (d < bestDist) {
                    bestDist = d
                    best = term
                }
            }
            val threshold = if (cleaned.length <= 5) 1 else 2
            if (best != null && bestDist <= threshold) {
                words[idx] = raw.replace(cleaned, best)
            }
        }
        return words.joinToString(" ")
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
