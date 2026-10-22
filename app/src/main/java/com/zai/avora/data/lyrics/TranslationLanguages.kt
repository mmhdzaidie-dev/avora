package com.zai.avora.data.lyrics

import java.util.Locale

data class TranslationLanguage(val code: String, val name: String)

object TranslationLanguages {

    const val FALLBACK = "id"

    val all: List<TranslationLanguage> = listOf(
        "af" to "Afrikaans", "sq" to "Albanian (Shqip)", "am" to "Amharic (አማርኛ)",
        "ar" to "Arabic (العربية)", "hy" to "Armenian (Հայերեն)", "as" to "Assamese (অসমীয়া)",
        "ay" to "Aymara", "az" to "Azerbaijani (Azərbaycan)", "bm" to "Bambara",
        "eu" to "Basque (Euskara)", "be" to "Belarusian (Беларуская)", "bn" to "Bengali (বাংলা)",
        "bho" to "Bhojpuri", "bs" to "Bosnian (Bosanski)", "bg" to "Bulgarian (Български)",
        "ca" to "Catalan (Català)", "ceb" to "Cebuano", "ny" to "Chichewa",
        "zh-CN" to "Chinese Simplified (简体中文)", "zh-TW" to "Chinese Traditional (繁體中文)",
        "co" to "Corsican", "hr" to "Croatian (Hrvatski)", "cs" to "Czech (Čeština)",
        "da" to "Danish (Dansk)", "dv" to "Dhivehi", "doi" to "Dogri", "nl" to "Dutch (Nederlands)",
        "en" to "English", "eo" to "Esperanto", "et" to "Estonian (Eesti)", "ee" to "Ewe",
        "tl" to "Filipino (Tagalog)", "fi" to "Finnish (Suomi)", "fr" to "French (Français)",
        "fy" to "Frisian", "gl" to "Galician (Galego)", "ka" to "Georgian (ქართული)",
        "de" to "German (Deutsch)", "el" to "Greek (Ελληνικά)", "gn" to "Guarani",
        "gu" to "Gujarati (ગુજરાતી)", "ht" to "Haitian Creole", "ha" to "Hausa",
        "haw" to "Hawaiian", "iw" to "Hebrew (עברית)", "hi" to "Hindi (हिन्दी)", "hmn" to "Hmong",
        "hu" to "Hungarian (Magyar)", "is" to "Icelandic (Íslenska)", "ig" to "Igbo",
        "ilo" to "Ilocano", "id" to "Indonesian (Indonesia)", "ga" to "Irish (Gaeilge)",
        "it" to "Italian (Italiano)", "ja" to "Japanese (日本語)", "jw" to "Javanese (Jawa)",
        "kn" to "Kannada (ಕನ್ನಡ)", "kk" to "Kazakh (Қазақ)", "km" to "Khmer (ខ្មែរ)",
        "rw" to "Kinyarwanda", "gom" to "Konkani", "ko" to "Korean (한국어)", "kri" to "Krio",
        "ku" to "Kurdish (Kurmanji)", "ckb" to "Kurdish (Sorani)", "ky" to "Kyrgyz (Кыргызча)",
        "lo" to "Lao (ລາວ)", "la" to "Latin", "lv" to "Latvian (Latviešu)", "ln" to "Lingala",
        "lt" to "Lithuanian (Lietuvių)", "lg" to "Luganda", "lb" to "Luxembourgish",
        "mk" to "Macedonian (Македонски)", "mai" to "Maithili", "mg" to "Malagasy",
        "ms" to "Malay (Bahasa Melayu)", "ml" to "Malayalam (മലയാളം)", "mt" to "Maltese",
        "mi" to "Maori", "mr" to "Marathi (मराठी)", "mni-Mtei" to "Meiteilon (Manipuri)",
        "lus" to "Mizo", "mn" to "Mongolian (Монгол)", "my" to "Myanmar (မြန်မာ)",
        "ne" to "Nepali (नेपाली)", "no" to "Norwegian (Norsk)", "or" to "Odia (ଓଡ଼ିଆ)",
        "om" to "Oromo", "ps" to "Pashto (پښتو)", "fa" to "Persian (فارسی)",
        "pl" to "Polish (Polski)", "pt" to "Portuguese (Português)", "pa" to "Punjabi (ਪੰਜਾਬੀ)",
        "qu" to "Quechua", "ro" to "Romanian (Română)", "ru" to "Russian (Русский)",
        "sm" to "Samoan", "sa" to "Sanskrit", "gd" to "Scots Gaelic", "nso" to "Sepedi",
        "sr" to "Serbian (Српски)", "st" to "Sesotho", "sn" to "Shona", "sd" to "Sindhi",
        "si" to "Sinhala (සිංහල)", "sk" to "Slovak (Slovenčina)", "sl" to "Slovenian (Slovenščina)",
        "so" to "Somali", "es" to "Spanish (Español)", "su" to "Sundanese (Sunda)",
        "sw" to "Swahili (Kiswahili)", "sv" to "Swedish (Svenska)", "tg" to "Tajik",
        "ta" to "Tamil (தமிழ்)", "tt" to "Tatar", "te" to "Telugu (తెలుగు)", "th" to "Thai (ไทย)",
        "ti" to "Tigrinya", "ts" to "Tsonga", "tr" to "Turkish (Türkçe)", "tk" to "Turkmen",
        "ak" to "Twi", "uk" to "Ukrainian (Українська)", "ur" to "Urdu (اردو)",
        "ug" to "Uyghur", "uz" to "Uzbek (Oʻzbek)", "vi" to "Vietnamese (Tiếng Việt)",
        "cy" to "Welsh (Cymraeg)", "xh" to "Xhosa", "yi" to "Yiddish", "yo" to "Yoruba",
        "zu" to "Zulu"
    ).map { TranslationLanguage(it.first, it.second) }

    private val byCode = all.associateBy { it.code }

    fun isSupported(code: String?): Boolean = code != null && byCode.containsKey(code)

    fun nameOf(code: String): String = byCode[code]?.name ?: code

    fun shortLabel(code: String): String = code.uppercase(Locale.ROOT)

    fun deviceDefault(): String {
        val locale = Locale.getDefault()
        val lang = when (locale.language) {
            "in" -> "id"
            "he" -> "iw"
            "jv" -> "jw"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            "zh" -> if (locale.script.equals("Hant", true) || locale.country in listOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            else -> locale.language
        }
        return if (isSupported(lang)) lang else FALLBACK
    }
}
