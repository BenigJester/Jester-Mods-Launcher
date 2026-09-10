package com.moodtools.hub

import com.moodtools.hub.modules.LauncherLanguage
import java.util.concurrent.ConcurrentHashMap

internal object LauncherAdditionalTranslations {
    private data class Template(
        val translations: Array<String>,
        val pattern: Regex,
        val placeholders: List<Int>
    )

    private val placeholder = Regex("\\{(\\d+)\\}")
    private val entries = buildMap {
        put(
            "Settings, launcher update available",
            arrayOf(
                "Mga Setting, may update sa launcher", "\uC124\uC815, \uB7F0\uCC98 \uC5C5\uB370\uC774\uD2B8 \uC0AC\uC6A9 \uAC00\uB2A5",
                "\u8A2D\u5B9A\u3001\u30E9\u30F3\u30C1\u30E3\u30FC\u66F4\u65B0\u3042\u308A", "\u8BBE\u7F6E\uFF0C\u542F\u52A8\u5668\u6709\u53EF\u7528\u66F4\u65B0",
                "Ajustes, actualizaci\u00F3n del lanzador disponible", "C\u00E0i \u0111\u1EB7t, c\u00F3 b\u1EA3n c\u1EADp nh\u1EADt tr\u00ECnh kh\u1EDFi ch\u1EA1y",
                "Pengaturan, pembaruan peluncur tersedia", "Configura\u00E7\u00F5es, atualiza\u00E7\u00E3o do inicializador dispon\u00EDvel"
            )
        )
        put(
            "Private access {0}, available until {1}",
            arrayOf(
                "Pribadong access {0}, magagamit hanggang {1}", "\uBE44\uACF5\uAC1C \uC561\uC138\uC2A4 {0}, {1}\uAE4C\uC9C0 \uC0AC\uC6A9 \uAC00\uB2A5",
                "\u30D7\u30E9\u30A4\u30D9\u30FC\u30C8\u30A2\u30AF\u30BB\u30B9 {0}\u3001{1} \u307E\u3067\u5229\u7528\u53EF\u80FD", "\u79C1\u4EBA\u8BBF\u95EE\u6743\u9650{0}\uFF0C\u6709\u6548\u671F\u81F3{1}",
                "Acceso privado {0}, disponible hasta {1}", "Quy\u1EC1n truy c\u1EADp ri\u00EAng t\u01B0 {0}, kh\u1EA3 d\u1EE5ng \u0111\u1EBFn {1}",
                "Akses privat {0}, tersedia hingga {1}", "Acesso privado {0}, dispon\u00EDvel at\u00E9 {1}"
            )
        )
        put(
            "Private add-on, approval is verified before use",
            arrayOf(
                "Pribadong add-on, bine-verify ang pag-apruba bago gamitin", "\uBE44\uACF5\uAC1C \uC560\uB4DC\uC628, \uC0AC\uC6A9 \uC804\uC5D0 \uC2B9\uC778\uC744 \uD655\uC778\uD569\uB2C8\uB2E4",
                "\u30D7\u30E9\u30A4\u30D9\u30FC\u30C8\u30A2\u30C9\u30AA\u30F3\u3001\u4F7F\u7528\u524D\u306B\u627F\u8A8D\u3092\u78BA\u8A8D\u3057\u307E\u3059", "\u79C1\u4EBA\u9644\u52A0\u7EC4\u4EF6\uFF0C\u4F7F\u7528\u524D\u4F1A\u9A8C\u8BC1\u6388\u6743",
                "Complemento privado, la aprobaci\u00F3n se verifica antes de usarlo", "Ti\u1EC7n \u00EDch ri\u00EAng t\u01B0, quy\u1EC1n ph\u00EA duy\u1EC7t \u0111\u01B0\u1EE3c x\u00E1c minh tr\u01B0\u1EDBc khi s\u1EED d\u1EE5ng",
                "Add-on privat, persetujuan diverifikasi sebelum digunakan", "Complemento privado, a aprova\u00E7\u00E3o \u00E9 verificada antes do uso"
            )
        )
        put(
            "Unselect {0}",
            arrayOf(
                "Alisin sa pagkakapili ang {0}", "{0} \uC120\uD0DD \uD574\uC81C", "{0} \u306E\u9078\u629E\u3092\u89E3\u9664", "\u53D6\u6D88\u9009\u62E9{0}",
                "Anular selecci\u00F3n de {0}", "B\u1ECF ch\u1ECDn {0}", "Batal pilih {0}", "Desmarcar {0}"
            )
        )
        put(
            "Select {0}",
            arrayOf(
                "Piliin ang {0}", "{0} \uC120\uD0DD", "{0} \u3092\u9078\u629E", "\u9009\u62E9{0}",
                "Seleccionar {0}", "Ch\u1ECDn {0}", "Pilih {0}", "Selecionar {0}"
            )
        )
        putAll(LauncherTranslationChunk0.entries)
        putAll(LauncherTranslationChunk1.entries)
        putAll(LauncherTranslationChunk2.entries)
        putAll(LauncherTranslationChunk3.entries)
        putAll(LauncherTranslationChunk4.entries)
        putAll(LauncherTranslationChunk5.entries)
        putAll(LauncherTranslationChunk6.entries)
        putAll(LauncherTranslationChunk7.entries)
        putAll(LauncherTranslationChunk8.entries)
        putAll(LauncherTranslationChunk9.entries)
        putAll(LauncherTranslationChunk10.entries)
        putAll(LauncherTranslationChunk11.entries)
        putAll(LauncherTranslationChunk12.entries)
        putAll(LauncherTranslationChunk13.entries)
        putAll(LauncherTranslationChunk14.entries)
        putAll(LauncherTranslationChunk15.entries)
        putAll(LauncherTranslationChunk16.entries)
        putAll(LauncherTranslationChunk17.entries)
        putAll(LauncherTranslationChunk18.entries)
    }
    private val exact = entries.filterKeys { !placeholder.containsMatchIn(it) }
    private val templates = entries
        .filterKeys(placeholder::containsMatchIn)
        .map { (source, translations) -> compile(source, translations) }
        .sortedByDescending { it.pattern.pattern.length }
    private val cache = ConcurrentHashMap<String, String>()
    internal val sourceText: Set<String> get() = entries.keys

    fun translate(text: String, language: LauncherLanguage): String? {
        if (language == LauncherLanguage.English) return null
        val languageIndex = language.ordinal - 1
        exact[text]?.getOrNull(languageIndex)?.takeIf(String::isNotBlank)?.let { return it }
        val cacheKey = "$languageIndex\u0000$text"
        cache[cacheKey]?.let { return it }
        return templates.firstNotNullOfOrNull { template ->
            val match = template.pattern.matchEntire(text) ?: return@firstNotNullOfOrNull null
            template.translations.getOrNull(languageIndex)
                ?.takeIf(String::isNotBlank)
                ?.let { translated ->
                    placeholder.replace(translated) { token ->
                        val requested = token.groupValues[1].toInt()
                        val capture = template.placeholders.indexOf(requested)
                        val value = match.groupValues.getOrElse(capture + 1) { token.value }
                        LauncherLocalization.translate(value, language)
                    }
                }
        }?.also { cache[cacheKey] = it }
    }

    private fun compile(source: String, translations: Array<String>): Template {
        val placeholders = mutableListOf<Int>()
        val pattern = buildString {
            append('^')
            var cursor = 0
            placeholder.findAll(source).forEach { match ->
                append(Regex.escape(source.substring(cursor, match.range.first)))
                append("(.*?)")
                placeholders += match.groupValues[1].toInt()
                cursor = match.range.last + 1
            }
            append(Regex.escape(source.substring(cursor)))
            append('$')
        }
        return Template(translations, Regex(pattern), placeholders)
    }
}
