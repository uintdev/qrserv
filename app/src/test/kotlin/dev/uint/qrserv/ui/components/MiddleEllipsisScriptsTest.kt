package dev.uint.qrserv.ui.components

import org.junit.Assert.assertTrue
import org.junit.Test

class MiddleEllipsisScriptsTest {

    private val widthOf: (String) -> Int = { it.codePointCount(0, it.length) * 10 }

    private fun isCombining(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> c == '‍'
    }

    private fun defect(s: String): String? {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isHighSurrogate()) {
                if (i + 1 >= s.length || !s[i + 1].isLowSurrogate()) return "lone high surrogate"
                i += 2
                continue
            }
            if (c.isLowSurrogate()) return "lone low surrogate"
            i++
        }
        val e = s.indexOf('…')
        if (e >= 0) {
            if (e + 1 < s.length && isCombining(s[e + 1])) return "orphaned combining mark"
            if (e > 0 && s[e - 1] == '‍') return "dangling ZWJ"
        }
        return null
    }

    private val samples = linkedMapOf(
        "ascii" to "plain_ascii_filename_that_is_quite_long_indeed.txt",
        "emoji" to "😀".repeat(12) + ".txt",
        "emoji A-prefixed" to "A" + "😀".repeat(12) + ".txt",
        "emoji AB-prefixed" to "AB" + "😀".repeat(12) + ".txt",
        "emoji ZWJ family" to "👨‍👩‍👧‍👦 family album.jpg",
        "emoji flags" to "🇬🇧🇫🇷🇩🇪 flags of europe.png",
        "emoji skin tones" to "👋🏽👋🏿 waving hands tones.gif",
        "zh-Hans" to "这是一个很长的中文文件名用于测试.txt",
        "zh-Hant" to "這是一個很長的中文檔案名稱用於測試.txt",
        "zh CJK Ext-B" to "𠀋𠀍𠀒𠀗𠐊𠠁𠰅𡀋.txt",
        "ja kana+kanji" to "これは日本語のファイル名のテストです.txt",
        "ja decomposed dakuten" to "がぎぐげござじず.txt",
        "ko precomposed" to "한국어파일이름테스트입니다.txt",
        "ko conjoining jamo" to "한글파걀한.txt",
        "th Thai" to "ทดสอบชื่อไฟล์ภาษาไทย.txt",
        "hi Devanagari" to "यह एक लंबा हिंदी फ़ाइल नाम.txt",
        "ar Arabic" to "اسم ملف عربي طويل جدا.txt",
        "he Hebrew" to "שם קובץ עברי ארוך מאוד.txt",
        "ru precomposed" to "Это очень длинное русское имя файла.txt",
        "ru decomposed й/ё" to "Моё новое видео Русскй файл.txt",
        "ru stress marks" to "за́мок замо́к ру́сский язы́к.txt",
        "ru decomposed dense" to "йё".repeat(12) + ".txt",
        "cyrillic extras" to "Їжак ґанок Џорђе Њива Љюбав.txt",
        "latin accents NFD" to "résumé café näïve über fa̧cade long name.txt",
    )

    @Test
    fun cutsCleanlyAcrossEveryScript() {
        val broken = linkedMapOf<String, String>()
        for ((label, sample) in samples) {
            for (budget in 0..(widthOf(sample) + 20) step 5) {
                for (out in listOf(
                    middleEllipsis(sample, budget, widthOf),
                    middleEllipsisFilename(sample, budget, widthOf),
                )) {
                    val d = defect(out)
                    if (d != null && !broken.containsKey(label)) {
                        broken[label] = "budget=$budget $d -> ${out.replace('…', '~')}"
                    }
                }
            }
        }
        assertTrue(
            "DEFECTIVE(${broken.size}/${samples.size}) >> " +
                broken.entries.joinToString(" | ") { "${it.key}: ${it.value}" },
            broken.isEmpty(),
        )
    }
}
