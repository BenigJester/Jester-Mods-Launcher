package com.moodtools.hub

import android.content.Context
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.moodtools.hub.modules.LauncherLanguage

internal object LauncherLocalization {
    private const val PREFERENCES = "launcher_settings"
    private const val LANGUAGE = "menu_language"
    private var initialized = false

    var language by mutableStateOf(LauncherLanguage.English)
        private set

    fun initialize(context: Context) {
        if (initialized) return
        language = LauncherLanguage.fromPreference(
            context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getInt(LANGUAGE, 0)
        )
        initialized = true
    }

    fun select(context: Context, selected: LauncherLanguage) {
        language = selected
        initialized = true
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putInt(LANGUAGE, selected.ordinal).apply()
    }

    fun needsLanguageConfirmation(context: Context): Boolean =
        !context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(LANGUAGE_CONFIRMED, false)

    fun confirmLanguage(context: Context, selected: LauncherLanguage) {
        language = selected
        initialized = true
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(LANGUAGE, selected.ordinal)
            .putBoolean(LANGUAGE_CONFIRMED, true)
            .apply()
    }

    fun translate(text: String): String = translate(text, language)

    internal fun translate(text: String, selected: LauncherLanguage): String {
        if (selected == LauncherLanguage.English || text.isBlank()) return text
        if (selected == LauncherLanguage.Portuguese) {
            PORTUGUESE[text]?.takeUnless { it == text }?.let { return it }
        }
        TRANSLATIONS[text]?.getOrNull(selected.ordinal - 1)
            ?.takeIf { it.isNotBlank() && it != text }
            ?.let { return it }
        if (text.startsWith("‹  ")) return "‹  ${translate(text.removePrefix("‹  "), selected)}"
        return LauncherAdditionalTranslations.translate(text, selected)
            ?: translateTemplate(text, selected).takeUnless { it == text }
            ?: translateComposite(text, selected)
    }

    private fun translateTemplate(text: String, selected: LauncherLanguage): String {
        val templates = when (selected) {
            LauncherLanguage.Filipino -> arrayOf("Version %s", "Build %s", "%s ang naka-install", "Alisin ang %s?", "I-clear ang data ng %s?")
            LauncherLanguage.Korean -> arrayOf("버전 %s", "빌드 %s", "%s개 설치됨", "%s을(를) 제거할까요?", "%s 데이터를 지울까요?")
            LauncherLanguage.Japanese -> arrayOf("バージョン %s", "ビルド %s", "%s 個インストール済み", "%s を削除しますか？", "%s のデータを消去しますか？")
            LauncherLanguage.ChineseSimplified -> arrayOf("版本 %s", "构建号 %s", "已安装 %s 个", "移除 %s？", "清除 %s 的数据？")
            LauncherLanguage.Spanish -> arrayOf("Versión %s", "Compilación %s", "%s instalados", "¿Quitar %s?", "¿Borrar los datos de %s?")
            LauncherLanguage.Vietnamese -> arrayOf("Phiên bản %s", "Bản dựng %s", "Đã cài %s", "Gỡ %s?", "Xóa dữ liệu %s?")
            LauncherLanguage.Indonesian -> arrayOf("Versi %s", "Build %s", "%s terpasang", "Hapus %s?", "Hapus data %s?")
            LauncherLanguage.Portuguese -> arrayOf("Versão %s", "Compilação %s", "%s instalados", "Remover %s?", "Limpar dados de %s?")
            LauncherLanguage.Arabic -> arrayOf("الإصدار %s", "البنية %s", "تم تثبيت %s", "هل تريد إزالة %s؟", "هل تريد مسح بيانات %s؟")
            LauncherLanguage.English -> return text
        }
        val language = selected.ordinal - 1
        return when {
            text.startsWith("Version ") -> templates[0].format(text.removePrefix("Version "))
            text.startsWith("Build ") -> templates[1].format(text.removePrefix("Build "))
            text.endsWith(" installed") -> templates[2].format(text.removeSuffix(" installed"))
            text.startsWith("Remove ") && text.endsWith("?") -> templates[3].format(text.removePrefix("Remove ").removeSuffix("?"))
            text.startsWith("Clear ") && text.endsWith(" data?") -> templates[4].format(text.removePrefix("Clear ").removeSuffix(" data?"))
            text.startsWith("Copied ") -> TEMPLATE_COPIED[language].format(text.removePrefix("Copied "))
            text.startsWith("Game version ") -> TEMPLATE_GAME_VERSION[language].format(text.removePrefix("Game version "))
            text.startsWith("Load ") && text.endsWith(" more") -> TEMPLATE_LOAD_MORE[language].format(text.removePrefix("Load ").removeSuffix(" more"))
            text.startsWith("Remove selected (") && text.endsWith(")") -> TEMPLATE_REMOVE_SELECTED[language].format(text.removePrefix("Remove selected (").removeSuffix(")"))
            text.startsWith("Sort: ") -> TEMPLATE_SORT[language].format(translate(text.removePrefix("Sort: "), selected))
            text.startsWith("Opening from ") -> TEMPLATE_OPENING_FROM[language].format(translate(text.removePrefix("Opening from "), selected))
            text.startsWith("Ready to ") -> TEMPLATE_READY_TO[language].format(translate(text.removePrefix("Ready to "), selected))
            text.startsWith("Use ") && text.endsWith(" instead") -> TEMPLATE_USE_INSTEAD[language].format(text.removePrefix("Use ").removeSuffix(" instead"))
            text.startsWith("Restore the official ") -> TEMPLATE_RESTORE_OFFICIAL[language].format(text.removePrefix("Restore the official "))
            else -> text
        }
    }

    private fun translateComposite(text: String, selected: LauncherLanguage): String {
        for (separator in listOf(" · ", "\n")) {
            if (separator !in text) continue
            val source = text.split(separator)
            val translated = source.map { segment ->
                val content = segment.trim()
                segment.replace(content, translate(content, selected))
            }
            if (translated.zip(source).any { (after, before) -> after != before }) {
                return translated.joinToString(separator)
            }
        }
        return text
    }

    private val TEMPLATE_COPIED = arrayOf("Nakopya ang %s", "%s 복사됨", "%s をコピーしました", "已复制 %s", "%s copiado", "Đã sao chép %s", "%s disalin", "%s copiado", "تم نسخ %s")
    private val TEMPLATE_GAME_VERSION = arrayOf("Bersyon ng laro %s", "게임 버전 %s", "ゲームバージョン %s", "游戏版本 %s", "Versión del juego %s", "Phiên bản trò chơi %s", "Versi game %s", "Versão do jogo %s", "إصدار اللعبة %s")
    private val TEMPLATE_LOAD_MORE = arrayOf("Mag-load pa ng %s", "%s개 더 불러오기", "さらに %s 件読み込む", "再加载 %s 个", "Cargar %s más", "Tải thêm %s", "Muat %s lagi", "Carregar mais %s", "تحميل %s إضافية")
    private val TEMPLATE_REMOVE_SELECTED = arrayOf("Alisin ang napili (%s)", "선택 항목 제거 (%s)", "選択項目を削除 (%s)", "移除所选项 (%s)", "Quitar seleccionados (%s)", "Gỡ mục đã chọn (%s)", "Hapus pilihan (%s)", "Remover selecionados (%s)", "إزالة المحدد (%s)")
    private val TEMPLATE_SORT = arrayOf("Ayos: %s", "정렬: %s", "並び順: %s", "排序：%s", "Ordenar: %s", "Sắp xếp: %s", "Urutkan: %s", "Ordenar: %s", "الترتيب: %s")
    private val TEMPLATE_OPENING_FROM = arrayOf("Binubuksan mula sa %s", "%s에서 여는 중", "%s から開いています", "正在从 %s 打开", "Abriendo desde %s", "Đang mở từ %s", "Membuka dari %s", "Abrindo de %s", "جارٍ الفتح من %s")
    private val TEMPLATE_READY_TO = arrayOf("Handang %s", "%s 준비 완료", "%s の準備完了", "已准备好%s", "Listo para %s", "Sẵn sàng %s", "Siap untuk %s", "Pronto para %s", "جاهز لـ %s")
    private val TEMPLATE_USE_INSTEAD = arrayOf("Gamitin na lang ang %s", "대신 %s 사용", "代わりに %s を使用", "改用 %s", "Usar %s en su lugar", "Dùng %s thay thế", "Gunakan %s sebagai gantinya", "Usar %s", "استخدم %s بدلاً منه")
    private val TEMPLATE_RESTORE_OFFICIAL = arrayOf("Ibalik ang opisyal na %s", "공식 %s 복원", "公式の %s を復元", "恢复官方 %s", "Restaurar %s oficial", "Khôi phục %s chính thức", "Pulihkan %s resmi", "Restaurar %s oficial", "استعادة %s الرسمية")

    private val PORTUGUESE = mapOf(
        "Settings" to "Configurações", "Language" to "Idioma", "Theme" to "Tema",
        "My Information" to "Minhas informações", "Changelog" to "Histórico de alterações",
        "About" to "Sobre", "Help" to "Ajuda", "Library" to "Biblioteca", "Browse add-ons" to "Explorar complementos",
        "Launcher" to "Inicializador", "Back" to "Voltar", "Try again" to "Tentar novamente",
        "Continue" to "Continuar", "Exit" to "Sair", "Cancel" to "Cancelar", "Close" to "Fechar",
        "Done" to "Concluído", "Later" to "Mais tarde", "Not now" to "Agora não",
        "Details" to "Detalhes", "Diagnostics" to "Diagnóstico", "Copy" to "Copiar",
        "Clear" to "Limpar", "Clear data" to "Limpar dados", "Remove" to "Remover",
        "Remove from Library" to "Remover da biblioteca", "Find add-ons" to "Encontrar complementos",
        "Search add-ons or packages" to "Pesquisar complementos ou pacotes",
        "Game name or package" to "Nome ou pacote do jogo", "No add-ons found" to "Nenhum complemento encontrado",
        "No games ready yet" to "Nenhum jogo pronto", "Loading your library" to "Carregando sua biblioteca",
        "Launcher update" to "Atualização do inicializador", "Launcher update available" to "Atualização disponível",
        "WHAT'S INCLUDED" to "O QUE ESTÁ INCLUÍDO", "WHAT'S NEW" to "NOVIDADES",
        "COMPATIBILITY" to "COMPATIBILIDADE", "OUTDATED" to "DESATUALIZADO", "UPDATES" to "ATUALIZAÇÕES",
        "AVAILABLE LANGUAGES" to "IDIOMAS DISPONÍVEIS", "CURATED THEMES" to "TEMAS SELECIONADOS",
        "Choose your preferred language for Jester Mods." to "Escolha seu idioma preferido para o Jester Mods.",
        "Your choice is saved offline on this device." to "Sua escolha fica salva offline neste dispositivo.",
        "Play" to "Jogar", "Play again" to "Jogar novamente", "Resume" to "Continuar",
        "Update" to "Atualizar", "Check again" to "Verificar novamente", "Ready" to "Pronto",
        "Open GitHub" to "Abrir GitHub", "Watch on YouTube" to "Assistir no YouTube",
        "Support code copied" to "Código de suporte copiado", "Diagnostics copied" to "Diagnóstico copiado",
        "RELEASE JOURNAL" to "DIÁRIO DE LANÇAMENTOS", "LAUNCHER" to "INICIALIZADOR",
        "The launcher, refined release by release." to "O inicializador, aprimorado a cada lançamento.",
        "Every supported game has its own story." to "Cada jogo compatível tem sua própria história.",
        "JESTER MODS ARCHIVE" to "ARQUIVO JESTER MODS", "ADD-ON ARCHIVE" to "ARQUIVO DE COMPLEMENTOS",
        "Release history" to "Histórico de lançamentos", "RELEASES" to "LANÇAMENTOS",
        "YOUR LIBRARY" to "SUA BIBLIOTECA", "ADD-ONS" to "COMPLEMENTOS",
        "READY" to "PRONTOS", "TO REVIEW" to "PARA REVISAR", "Manage" to "Gerenciar",
        "A polished home for every add-on you keep close." to "Um espaço refinado para cada complemento que você mantém por perto.",
        "ADD-ON CATALOG" to "CATÁLOGO DE COMPLEMENTOS", "MATCHES" to "COMPATÍVEIS",
        "CATEGORIES" to "CATEGORIAS", "DISCOVER" to "DESCOBRIR",
        "CARE & RECOVERY" to "CUIDADO E RECUPERAÇÃO", "Fresh start, same add-on." to "Novo começo, mesmo complemento.",
        "Download a fresh, verified copy of this add-on whenever its files need attention." to "Baixe uma cópia nova e verificada deste complemento sempre que os arquivos precisarem de atenção.",
        "Repair add-on" to "Reparar complemento",
        "Catalog unavailable" to "Catálogo indisponível", "Your collection is complete" to "Sua coleção está completa",
        "Discover your next game upgrade in a catalog curated for this device." to "Descubra sua próxima melhoria em um catálogo selecionado para este dispositivo.",
        "HELP & SUPPORT" to "AJUDA E SUPORTE",
        "Find guidance and report issues through the official public repository." to "Encontre orientações e relate problemas pelo repositório público oficial.",
        "JESTER MODS SUPPORT" to "SUPORTE JESTER MODS", "Help & Support" to "Ajuda e suporte",
        "Turn a problem into a clear report so it can be understood and fixed faster." to "Transforme um problema em um relato claro para que ele seja entendido e corrigido mais rápido.",
        "BEFORE YOU REPORT" to "ANTES DE RELATAR", "Update first" to "Atualize primeiro",
        "Install the latest launcher and add-on update, then try the action again." to "Instale a atualização mais recente do inicializador e do complemento e tente a ação novamente.",
        "Capture the details" to "Registre os detalhes",
        "Include your launcher edition, game version, build number, and the steps that caused the issue." to "Inclua a edição do inicializador, a versão e o número de compilação do jogo e as etapas que causaram o problema.",
        "Protect your privacy" to "Proteja sua privacidade",
        "Never post access codes, recovery details, or other personal information." to "Nunca publique códigos de acesso, detalhes de recuperação ou outras informações pessoais.",
        "OFFICIAL SUPPORT" to "SUPORTE OFICIAL", "Report an issue" to "Relatar um problema",
        "Open the public issue page to report a bug, request help, or suggest an improvement." to "Abra a página pública de problemas para relatar um erro, pedir ajuda ou sugerir uma melhoria.",
        "Open issue page" to "Abrir página de problemas"
    )

    // Filipino, Korean, Japanese, Simplified Chinese, Spanish, Vietnamese, Indonesian, Arabic.
    private fun row(vararg values: String) = values.toList()
    private val TRANSLATIONS = mapOf(
        "Settings" to row("Mga Setting", "설정", "設定", "设置", "Ajustes", "Cài đặt", "Pengaturan", "", "الإعدادات"),
        "Language" to row("Wika", "언어", "言語", "语言", "Idioma", "Ngôn ngữ", "Bahasa", "", "اللغة"),
        "Theme" to row("Tema", "테마", "テーマ", "主题", "Tema", "Chủ đề", "Tema", "", "الموضوع"),
        "My Information" to row("Aking Impormasyon", "내 정보", "マイ情報", "我的信息", "Mi información", "Thông tin của tôi", "Informasi Saya", "", "معلوماتي"),
        "Changelog" to row("Talaan ng Pagbabago", "변경 내역", "変更履歴", "更新日志", "Registro de cambios", "Nhật ký thay đổi", "Catatan Perubahan", "", "سجل التغيير"),
        "About" to row("Tungkol", "정보", "このアプリについて", "关于", "Acerca de", "Giới thiệu", "Tentang", "", "حول"),
        "Help" to row("Tulong", "도움말", "ヘルプ", "帮助", "Ayuda", "Trợ giúp", "Bantuan", "", "مساعدة"),
        "Library" to row("Library", "라이브러리", "ライブラリ", "游戏库", "Biblioteca", "Thư viện", "Pustaka", "", "مكتبة"),
        "Browse add-ons" to row("Mag-browse ng mga add-on", "애드온 찾기", "アドオンを見る", "浏览附加组件", "Explorar complementos", "Duyệt tiện ích", "Jelajahi add-on", "", "تصفح الإضافات"),
        "Launcher" to row("Launcher", "런처", "ランチャー", "启动器", "Lanzador", "Trình khởi chạy", "Peluncur", "", "قاذفة"),
        "Back" to row("Bumalik", "뒤로", "戻る", "返回", "Atrás", "Quay lại", "Kembali", "", "العودة"),
        "‹  Back" to row("‹  Bumalik", "‹  뒤로", "‹  戻る", "‹  返回", "‹  Atrás", "‹  Quay lại", "‹  Kembali", "", "‹ العودة"),
        "‹  Settings" to row("‹  Mga Setting", "‹  설정", "‹  設定", "‹  设置", "‹  Ajustes", "‹  Cài đặt", "‹  Pengaturan", "", "‹ الإعدادات"),
        "Try again" to row("Subukan muli", "다시 시도", "もう一度試す", "重试", "Reintentar", "Thử lại", "Coba lagi", "", "حاول مرة أخرى"),
        "Continue" to row("Magpatuloy", "계속", "続行", "继续", "Continuar", "Tiếp tục", "Lanjutkan", "", "متابعة"),
        "Exit" to row("Lumabas", "종료", "終了", "退出", "Salir", "Thoát", "Keluar", "", "خروج"),
        "Cancel" to row("Kanselahin", "취소", "キャンセル", "取消", "Cancelar", "Hủy", "Batal", "", "إلغاء"),
        "Close" to row("Isara", "닫기", "閉じる", "关闭", "Cerrar", "Đóng", "Tutup", "", "إغلاق"),
        "Done" to row("Tapos na", "완료", "完了", "完成", "Listo", "Xong", "Selesai", "", "تم"),
        "Later" to row("Mamaya", "나중에", "後で", "稍后", "Más tarde", "Để sau", "Nanti", "", "في وقت لاحق"),
        "Not now" to row("Hindi ngayon", "나중에", "今はしない", "暂不", "Ahora no", "Không phải bây giờ", "Jangan sekarang", "", "ليس الآن"),
        "Details" to row("Mga Detalye", "세부 정보", "詳細", "详细信息", "Detalles", "Chi tiết", "Detail", "", "التفاصيل"),
        "Diagnostics" to row("Diagnostics", "진단", "診断", "诊断", "Diagnósticos", "Chẩn đoán", "Diagnostik", "", "التشخيص"),
        "Copy" to row("Kopyahin", "복사", "コピー", "复制", "Copiar", "Sao chép", "Salin", "", "نسخ"),
        "Clear" to row("I-clear", "지우기", "クリア", "清除", "Borrar", "Xóa", "Bersihkan", "", "واضح"),
        "Clear data" to row("I-clear ang data", "데이터 삭제", "データを消去", "清除数据", "Borrar datos", "Xóa dữ liệu", "Hapus data", "", "مسح البيانات"),
        "Remove" to row("Alisin", "제거", "削除", "移除", "Quitar", "Gỡ", "Hapus", "", "إزالة"),
        "Remove from Library" to row("Alisin sa Library", "라이브러리에서 제거", "ライブラリから削除", "从游戏库移除", "Quitar de la biblioteca", "Gỡ khỏi thư viện", "Hapus dari pustaka", "", "إزالة من المكتبة"),
        "Find add-ons" to row("Maghanap ng mga add-on", "애드온 찾기", "アドオンを検索", "查找附加组件", "Buscar complementos", "Tìm tiện ích", "Cari add-on", "", "البحث عن الإضافات"),
        "Search add-ons or packages" to row("Maghanap ng add-on o package", "애드온 또는 패키지 검색", "アドオンまたはパッケージを検索", "搜索附加组件或包", "Buscar complementos o paquetes", "Tìm tiện ích hoặc gói", "Cari add-on atau paket", "", "البحث عن الوظائف الإضافية أو الحزم"),
        "Game name or package" to row("Pangalan o package ng laro", "게임 이름 또는 패키지", "ゲーム名またはパッケージ", "游戏名称或包名", "Nombre o paquete del juego", "Tên trò chơi hoặc gói", "Nama game atau paket", "", "اسم اللعبة أو الحزمة"),
        "No add-ons found" to row("Walang nakitang add-on", "애드온을 찾을 수 없습니다", "アドオンが見つかりません", "未找到附加组件", "No se encontraron complementos", "Không tìm thấy tiện ích", "Add-on tidak ditemukan", "", "لم يتم العثور على أي إضافات"),
        "No games ready yet" to row("Wala pang larong handa", "준비된 게임이 아직 없습니다", "準備できたゲームはまだありません", "暂无就绪的游戏", "Aún no hay juegos listos", "Chưa có trò chơi sẵn sàng", "Belum ada game yang siap", "", "لا توجد ألعاب جاهزة بعد"),
        "Loading your library" to row("Nilo-load ang iyong library", "라이브러리 로드 중", "ライブラリを読み込み中", "正在加载游戏库", "Cargando tu biblioteca", "Đang tải thư viện", "Memuat pustaka Anda", "", "جارٍ تحميل مكتبتك"),
        "Launcher update" to row("Update ng launcher", "런처 업데이트", "ランチャー更新", "启动器更新", "Actualización del lanzador", "Cập nhật trình khởi chạy", "Pembaruan peluncur", "", "تحديث قاذفة"),
        "Launcher update available" to row("May update ang launcher", "런처 업데이트 사용 가능", "ランチャー更新があります", "有可用的启动器更新", "Actualización disponible", "Có bản cập nhật", "Pembaruan tersedia", "", "تحديث المشغل متاح"),
        "WHAT'S INCLUDED" to row("ANO ANG KASAMA", "포함된 내용", "含まれるもの", "包含内容", "QUÉ INCLUYE", "BAO GỒM", "YANG TERMASUK", "", "ما هو مدرج"),
        "WHAT'S NEW" to row("ANO ANG BAGO", "새로운 기능", "新着情報", "更新内容", "NOVEDADES", "CÓ GÌ MỚI", "YANG BARU", "", "ما هو الجديد"),
        "COMPATIBILITY" to row("COMPATIBILITY", "호환성", "互換性", "兼容性", "COMPATIBILIDAD", "KHẢ NĂNG TƯƠNG THÍCH", "KOMPATIBILITAS", "", "التوافق"),
        "OUTDATED" to row("LUMA NA", "구버전", "古いバージョン", "已过时", "DESACTUALIZADO", "LẠC HẬU", "KEDALUWARSA", "", "عفا عليه الزمن"),
        "UPDATES" to row("MGA UPDATE", "업데이트", "更新", "更新", "ACTUALIZACIONES", "CẬP NHẬT", "PEMBARUAN", "", "التحديثات"),
        "AVAILABLE LANGUAGES" to row("MGA WIKANG AVAILABLE", "사용 가능한 언어", "利用可能な言語", "可用语言", "IDIOMAS DISPONIBLES", "NGÔN NGỮ CÓ SẴN", "BAHASA TERSEDIA", "", "اللغات المتاحة"),
        "CURATED THEMES" to row("MGA PINILING TEMA", "엄선된 테마", "厳選テーマ", "精选主题", "TEMAS SELECCIONADOS", "CHỦ ĐỀ TUYỂN CHỌN", "TEMA PILIHAN", "", "موضوعات منسقة"),
        "Choose your preferred language for Jester Mods." to row("Piliin ang gusto mong wika para sa Jester Mods.", "Jester Mods에서 사용할 언어를 선택하세요.", "Jester Mods で使用する言語を選択してください。", "选择 Jester Mods 的首选语言。", "Elige tu idioma preferido para Jester Mods.", "Chọn ngôn ngữ ưa thích cho Jester Mods.", "Pilih bahasa pilihan Anda untuk Jester Mods.", "", "اختر لغتك المفضلة لـ Jester Mods."),
        "Your choice is saved offline on this device." to row("Naka-save offline sa device na ito ang pinili mo.", "선택한 언어는 이 기기에 오프라인으로 저장됩니다.", "選択内容はこのデバイスにオフラインで保存されます。", "您的选择将离线保存在此设备上。", "Tu elección se guarda sin conexión en este dispositivo.", "Lựa chọn được lưu ngoại tuyến trên thiết bị này.", "Pilihan Anda disimpan secara offline di perangkat ini.", "", "يتم حفظ اختيارك بلا اتصال على هذا الجهاز."),
        "Your theme is applied instantly and saved on this device." to row("Agad na inilalapat at sine-save sa device na ito ang tema.", "테마가 즉시 적용되고 이 기기에 저장됩니다.", "テーマはすぐに適用され、このデバイスに保存されます。", "主题会立即应用并保存在此设备上。", "El tema se aplica al instante y se guarda en este dispositivo.", "Chủ đề được áp dụng ngay và lưu trên thiết bị này.", "Tema diterapkan langsung dan disimpan di perangkat ini.", "", "يتم تطبيق المظهر الخاص بك على الفور وحفظه على هذا الجهاز."),
        "YOUR LAUNCHER" to row("IYONG LAUNCHER", "내 런처", "マイランチャー", "您的启动器", "TU LANZADOR", "TRÌNH KHỞI CHẠY CỦA BẠN", "PELUNCUR ANDA", "", "قاذفتك"),
        "YOUR ADD-ONS" to row("IYONG MGA ADD-ON", "내 애드온", "マイアドオン", "您的附加组件", "TUS COMPLEMENTOS", "TIỆN ÍCH CỦA BẠN", "ADD-ON ANDA", "", "الإضافات الخاصة بك"),
        "YOUR LIBRARY" to row("IYONG LIBRARY", "내 라이브러리", "マイライブラリ", "您的游戏库", "TU BIBLIOTECA", "THƯ VIỆN CỦA BẠN", "PUSTAKA ANDA", "", "مكتبتك"),
        "ADD-ONS" to row("MGA ADD-ON", "애드온", "アドオン", "附加组件", "COMPLEMENTOS", "TIỆN ÍCH", "ADD-ON", "", "الإضافات"),
        "READY" to row("HANDA", "준비됨", "準備完了", "就绪", "LISTOS", "SẴN SÀNG", "SIAP", "", "جاهز"),
        "TO REVIEW" to row("SURIIN", "확인 필요", "要確認", "待检查", "POR REVISAR", "CẦN XEM LẠI", "PERLU DITINJAU", "", "للمراجعة"),
        "Manage" to row("Pamahalaan", "관리", "管理", "管理", "Gestionar", "Quản lý", "Kelola", "", "إدارة"),
        "A polished home for every add-on you keep close." to row(
            "Isang pinong tahanan para sa bawat add-on na malapit sa iyo.",
            "소중한 모든 애드온을 위한 세련된 공간입니다.",
            "お気に入りのアドオンを集める洗練された場所です。",
            "为你珍藏的每个附加组件打造的精致空间。",
            "Un hogar elegante para cada complemento que quieres tener cerca.",
            "Một không gian tinh tế cho mọi tiện ích bạn luôn muốn giữ bên mình.",
            "Rumah elegan untuk setiap add-on yang selalu Anda simpan dekat.",
            "",
            "مساحة أنيقة لكل إضافة تحتفظ بها قريبة منك."
        ),
        "ADD-ON CATALOG" to row("CATALOG NG ADD-ON", "애드온 카탈로그", "アドオンカタログ", "附加组件目录", "CATÁLOGO DE COMPLEMENTOS", "DANH MỤC TIỆN ÍCH", "KATALOG ADD-ON", "", "كتالوج الإضافات"),
        "MATCHES" to row("TUGMA", "추천", "おすすめ", "匹配", "COMPATIBLES", "PHÙ HỢP", "COCOK", "", "مباريات"),
        "CATEGORIES" to row("MGA KATEGORYA", "카테고리", "カテゴリ", "类别", "CATEGORÍAS", "DANH MỤC", "KATEGORI", "", "الفئات"),
        "CARE & RECOVERY" to row("AYOS AT PAGBAWI", "관리 및 복구", "ケアと復旧", "维护与恢复", "CUIDADO Y RECUPERACIÓN", "CHĂM SÓC & KHÔI PHỤC", "PERAWATAN & PEMULIHAN", "", "الرعاية والتعافي"),
        "Fresh start, same add-on." to row("Bagong simula, parehong add-on.", "새롭게, 같은 애드온.", "同じアドオンを、新しく。", "焕然一新，仍是同一附加组件。", "Un nuevo comienzo, el mismo complemento.", "Khởi đầu mới, vẫn là tiện ích ấy.", "Awal baru, add-on yang sama.", "", "بداية جديدة، نفس الإضافة."),
        "Download a fresh, verified copy of this add-on whenever its files need attention." to row("Mag-download ng bago at beripikadong kopya ng add-on kapag kailangang ayusin ang mga file nito.", "파일에 문제가 있을 때 새로 검증된 애드온 사본을 다운로드하세요.", "ファイルに問題があるときは、検証済みの新しいアドオンをダウンロードします。", "当文件需要修复时，下载全新且经过验证的附加组件副本。", "Descarga una copia nueva y verificada del complemento cuando sus archivos necesiten atención.", "Tải bản sao mới đã xác minh của tiện ích khi các tệp cần được khôi phục.", "Unduh salinan add-on baru yang terverifikasi saat filenya perlu diperbaiki.", "", "قم بتنزيل نسخة جديدة تم التحقق منها من هذه الوظيفة الإضافية عندما تحتاج ملفاتها إلى الاهتمام."),
        "Repair add-on" to row("Ayusin ang add-on", "애드온 복구", "アドオンを修復", "修复附加组件", "Reparar complemento", "Sửa tiện ích", "Perbaiki add-on", "", "إصلاح الوظيفة الإضافية"),
        "DISCOVER" to row("TUKLASIN", "탐색", "見つける", "发现", "DESCUBRIR", "KHÁM PHÁ", "TEMUKAN", "", "اكتشف"),
        "Catalog unavailable" to row("Hindi available ang catalog", "카탈로그를 사용할 수 없음", "カタログを利用できません", "目录不可用", "Catálogo no disponible", "Danh mục không khả dụng", "Katalog tidak tersedia", "", "الكتالوج غير متاح"),
        "Your collection is complete" to row("Kumpleto na ang iyong koleksyon", "컬렉션이 완성되었습니다", "コレクションが完成しました", "您的收藏已完整", "Tu colección está completa", "Bộ sưu tập của bạn đã hoàn tất", "Koleksi Anda sudah lengkap", "", "مجموعتك كاملة"),
        "Discover your next game upgrade in a catalog curated for this device." to row(
            "Tuklasin ang susunod mong game upgrade sa catalog na pinili para sa device na ito.",
            "이 기기에 맞게 선별된 카탈로그에서 다음 게임 업그레이드를 찾아보세요.",
            "このデバイス向けに厳選されたカタログから、次のゲームアップグレードを見つけましょう。",
            "在为此设备精选的目录中发现下一款游戏升级。",
            "Descubre tu próxima mejora en un catálogo seleccionado para este dispositivo.",
            "Khám phá bản nâng cấp trò chơi tiếp theo trong danh mục được tuyển chọn cho thiết bị này.",
            "Temukan peningkatan game berikutnya dalam katalog pilihan untuk perangkat ini.",
            "",
            "اكتشف ترقية لعبتك التالية في كتالوج منسّق لهذا الجهاز."
        ),
        "Your games, ready when you are" to row("Handa ang iyong mga laro kapag handa ka na", "당신이 준비되면 게임도 준비됩니다", "いつでも遊べるゲーム", "游戏随时就绪", "Tus juegos, listos cuando tú lo estés", "Trò chơi sẵn sàng khi bạn sẵn sàng", "Game Anda siap saat Anda siap", "", "ألعابك جاهزة عندما تكون كذلك"),
        "EXPLORE" to row("GALUGARIN", "탐색", "見る", "探索", "EXPLORAR", "KHÁM PHÁ", "JELAJAHI", "", "اكتشف"),
        "ACCOUNT & SUPPORT" to row("ACCOUNT AT SUPORTA", "계정 및 지원", "アカウントとサポート", "账户与支持", "CUENTA Y SOPORTE", "TÀI KHOẢN & HỖ TRỢ", "AKUN & DUKUNGAN", "", "الحساب والدعم"),
        "RELEASE HISTORY" to row("KASAYSAYAN NG RELEASE", "릴리스 기록", "リリース履歴", "发布历史", "HISTORIAL DE VERSIONES", "LỊCH SỬ PHÁT HÀNH", "RIWAYAT RILIS", "", "تاريخ الإصدار"),
        "DISPLAY & LANGUAGE" to row("DISPLAY AT WIKA", "화면 및 언어", "表示と言語", "显示与语言", "PANTALLA E IDIOMA", "HIỂN THỊ & NGÔN NGỮ", "TAMPILAN & BAHASA", "", "العرض واللغة"),
        "LOOK & FEEL" to row("HITSURA AT DAMDAM", "룩 앤 필", "外観と操作感", "外观与体验", "ASPECTO", "GIAO DIỆN", "TAMPILAN", "", "انظر واشعر"),
        "View your support code, recovery identity, and launcher access details." to row("Tingnan ang support code, recovery identity, at mga detalye ng launcher access.", "지원 코드, 복구 신원, 런처 접근 세부 정보를 확인하세요.", "サポートコード、復旧ID、ランチャーのアクセス詳細を確認します。", "查看支持代码、恢复身份和启动器访问详情。", "Consulta tu código de soporte, identidad de recuperación y acceso.", "Xem mã hỗ trợ, danh tính khôi phục và chi tiết truy cập.", "Lihat kode dukungan, identitas pemulihan, dan detail akses.", "", "اعرض رمز الدعم وهوية الاسترداد وتفاصيل الوصول إلى المشغل."),
        "Explore launcher releases and the complete update history for your add-ons." to row("Tingnan ang mga launcher release at kumpletong update history ng iyong mga add-on.", "런처 릴리스와 애드온의 전체 업데이트 기록을 확인하세요.", "ランチャーのリリースとアドオンの全更新履歴を確認します。", "查看启动器发布版和附加组件的完整更新历史。", "Explora las versiones del lanzador y todo el historial de tus complementos.", "Khám phá các bản phát hành và toàn bộ lịch sử cập nhật tiện ích.", "Jelajahi rilis peluncur dan seluruh riwayat pembaruan add-on.", "", "استكشف إصدارات المشغّل وسجل التحديث الكامل للوظائف الإضافية الخاصة بك."),
        "Meet the launcher, its design principles, and the creator behind Jester Mods." to row("Kilalanin ang launcher, mga prinsipyo ng disenyo, at ang gumawa ng Jester Mods.", "런처와 디자인 원칙, Jester Mods의 제작자를 소개합니다.", "ランチャーとデザイン原則、Jester Mods の制作者を紹介します。", "了解启动器、设计原则以及 Jester Mods 背后的创作者。", "Conoce el lanzador, sus principios y al creador de Jester Mods.", "Tìm hiểu trình khởi chạy, nguyên tắc thiết kế và người sáng tạo Jester Mods.", "Kenali peluncur, prinsip desain, dan kreator Jester Mods.", "", "تعرف على المشغل ومبادئ التصميم الخاصة به والمبدع الذي يقف وراء Jester Mods."),
        "HELP & SUPPORT" to row("TULONG AT SUPORTA", "도움말 및 지원", "ヘルプとサポート", "帮助与支持", "AYUDA Y SOPORTE", "TRỢ GIÚP & HỖ TRỢ", "BANTUAN & DUKUNGAN", "", "المساعدة والدعم"),
        "Find guidance and report issues through the official public repository." to row("Humanap ng gabay at mag-ulat ng problema sa opisyal na pampublikong repository.", "공식 공개 저장소에서 안내를 확인하고 문제를 신고하세요.", "公式公開リポジトリでガイドを確認し、問題を報告できます。", "通过官方公共仓库查找指南并报告问题。", "Consulta la guía e informa de problemas mediante el repositorio público oficial.", "Tìm hướng dẫn và báo cáo sự cố qua kho lưu trữ công khai chính thức.", "Temukan panduan dan laporkan masalah melalui repositori publik resmi.", "", "ابحث عن التوجيهات وقم بالإبلاغ عن المشكلات من خلال المستودع العام الرسمي."),
        "JESTER MODS SUPPORT" to row("SUPORTA NG JESTER MODS", "JESTER MODS 지원", "JESTER MODS サポート", "JESTER MODS 支持", "SOPORTE DE JESTER MODS", "HỖ TRỢ JESTER MODS", "DUKUNGAN JESTER MODS", "", "دعم تعديل المهرج"),
        "Help & Support" to row("Tulong at Suporta", "도움말 및 지원", "ヘルプとサポート", "帮助与支持", "Ayuda y soporte", "Trợ giúp & Hỗ trợ", "Bantuan & Dukungan", "", "المساعدة والدعم"),
        "Turn a problem into a clear report so it can be understood and fixed faster." to row("Gawing malinaw na ulat ang problema upang mas mabilis itong maunawaan at maayos.", "문제를 명확한 보고서로 작성하면 더 빠르게 이해하고 해결할 수 있습니다.", "問題を明確な報告にまとめることで、より早く把握して修正できます。", "将问题整理成清晰的报告，以便更快理解和修复。", "Convierte el problema en un informe claro para entenderlo y resolverlo antes.", "Biến vấn đề thành báo cáo rõ ràng để có thể hiểu và khắc phục nhanh hơn.", "Ubah masalah menjadi laporan yang jelas agar lebih cepat dipahami dan diperbaiki.", "", "تحويل المشكلة إلى تقرير واضح حتى يمكن فهمها وحلها بشكل أسرع."),
        "BEFORE YOU REPORT" to row("BAGO MAG-ULAT", "신고하기 전에", "報告する前に", "报告之前", "ANTES DE INFORMAR", "TRƯỚC KHI BÁO CÁO", "SEBELUM MELAPOR", "", "قبل الإبلاغ"),
        "Update first" to row("Mag-update muna", "먼저 업데이트", "まず更新", "先更新", "Actualiza primero", "Cập nhật trước", "Perbarui dahulu", "", "قم بالتحديث أولاً"),
        "Install the latest launcher and add-on update, then try the action again." to row("I-install ang pinakabagong launcher at add-on update, saka subukan muli.", "최신 런처와 애드온 업데이트를 설치한 뒤 다시 시도하세요.", "最新のランチャーとアドオン更新をインストールしてから、もう一度お試しください。", "安装最新的启动器和附加组件更新，然后重试。", "Instala la última actualización del lanzador y del complemento y vuelve a intentarlo.", "Cài đặt bản cập nhật trình khởi chạy và tiện ích mới nhất, rồi thử lại.", "Instal pembaruan peluncur dan add-on terbaru, lalu coba lagi.", "", "قم بتثبيت آخر تحديث للمشغل والوظيفة الإضافية، ثم حاول تنفيذ الإجراء مرة أخرى."),
        "Capture the details" to row("Itala ang mga detalye", "세부 정보 기록", "詳細を記録", "记录详情", "Anota los detalles", "Ghi lại chi tiết", "Catat detailnya", "", "التقط التفاصيل"),
        "Include your launcher edition, game version, build number, and the steps that caused the issue." to row("Isama ang launcher edition, game version, build number, at mga hakbang na nagdulot ng problema.", "런처 에디션, 게임 버전, 빌드 번호와 문제가 발생한 단계를 포함하세요.", "ランチャーのエディション、ゲームバージョン、ビルド番号、問題が起きた手順を含めてください。", "请包含启动器版本、游戏版本、构建号以及导致问题的操作步骤。", "Incluye la edición del lanzador, la versión y compilación del juego y los pasos que causaron el problema.", "Hãy nêu phiên bản trình khởi chạy, phiên bản và số bản dựng trò chơi cùng các bước gây ra sự cố.", "Sertakan edisi peluncur, versi dan nomor build game, serta langkah yang memicu masalah.", "", "قم بتضمين إصدار المشغل وإصدار اللعبة ورقم الإصدار والخطوات التي تسببت في المشكلة."),
        "Protect your privacy" to row("Protektahan ang iyong privacy", "개인정보 보호", "プライバシーを保護", "保护隐私", "Protege tu privacidad", "Bảo vệ quyền riêng tư", "Lindungi privasi Anda", "", "حماية خصوصيتك"),
        "Never post access codes, recovery details, or other personal information." to row("Huwag mag-post ng access code, recovery detail, o iba pang personal na impormasyon.", "액세스 코드, 복구 정보 또는 기타 개인정보를 게시하지 마세요.", "アクセスコード、復旧情報、その他の個人情報は投稿しないでください。", "切勿发布访问代码、恢复详情或其他个人信息。", "Nunca publiques códigos de acceso, datos de recuperación ni otra información personal.", "Không đăng mã truy cập, thông tin khôi phục hoặc dữ liệu cá nhân khác.", "Jangan pernah memposting kode akses, detail pemulihan, atau informasi pribadi lainnya.", "", "لا تنشر مطلقًا رموز الوصول أو تفاصيل الاسترداد أو أي معلومات شخصية أخرى."),
        "OFFICIAL SUPPORT" to row("OPISYAL NA SUPORTA", "공식 지원", "公式サポート", "官方支持", "SOPORTE OFICIAL", "HỖ TRỢ CHÍNH THỨC", "DUKUNGAN RESMI", "", "الدعم الرسمي"),
        "Report an issue" to row("Mag-ulat ng problema", "문제 신고", "問題を報告", "报告问题", "Informar de un problema", "Báo cáo sự cố", "Laporkan masalah", "", "الإبلاغ عن مشكلة"),
        "Open the public issue page to report a bug, request help, or suggest an improvement." to row("Buksan ang pampublikong issue page para mag-ulat ng bug, humingi ng tulong, o magmungkahi ng improvement.", "공개 이슈 페이지에서 버그를 신고하거나 도움을 요청하고 개선 사항을 제안하세요.", "公開 Issue ページでバグの報告、サポートの依頼、改善案の提案ができます。", "打开公共问题页面以报告错误、请求帮助或提出改进建议。", "Abre la página pública de incidencias para informar de un error, pedir ayuda o sugerir una mejora.", "Mở trang sự cố công khai để báo lỗi, yêu cầu trợ giúp hoặc đề xuất cải tiến.", "Buka halaman masalah publik untuk melaporkan bug, meminta bantuan, atau menyarankan peningkatan.", "", "افتح صفحة المشكلة العامة للإبلاغ عن خطأ أو طلب المساعدة أو اقتراح تحسين."),
        "Open issue page" to row("Buksan ang issue page", "이슈 페이지 열기", "Issue ページを開く", "打开问题页面", "Abrir página de incidencias", "Mở trang sự cố", "Buka halaman masalah", "", "فتح صفحة القضية"),
        "Choose what works on this device" to row("Piliin ang gumagana sa device na ito", "이 기기에 맞는 방법을 선택하세요", "このデバイスに合う方法を選択", "选择适合此设备的方式", "Elige lo que funciona en este dispositivo", "Chọn phương thức phù hợp với thiết bị", "Pilih yang sesuai untuk perangkat ini", "", "اختر ما يعمل على هذا الجهاز"),
        "The add-on catalog is unavailable. Check your connection and try again." to row("Hindi available ang add-on catalog. Suriin ang koneksyon at subukan muli.", "애드온 카탈로그를 사용할 수 없습니다. 연결을 확인하고 다시 시도하세요.", "アドオンカタログを利用できません。接続を確認して再試行してください。", "附加组件目录不可用。请检查连接并重试。", "El catálogo no está disponible. Comprueba tu conexión e inténtalo de nuevo.", "Danh mục tiện ích không khả dụng. Kiểm tra kết nối và thử lại.", "Katalog add-on tidak tersedia. Periksa koneksi dan coba lagi.", "", "كتالوج الإضافات غير متوفر. تحقق من اتصالك وحاول مرة أخرى."),
        "Loading feature details…" to row("Lina-load ang detalye ng feature…", "기능 세부 정보 로드 중…", "機能の詳細を読み込み中…", "正在加载功能详情…", "Cargando detalles de funciones…", "Đang tải chi tiết tính năng…", "Memuat detail fitur…", "", "جارٍ تحميل تفاصيل الميزة…"),
        "Loading more add-ons…" to row("Lina-load ang iba pang add-on…", "애드온 더 로드 중…", "さらにアドオンを読み込み中…", "正在加载更多附加组件…", "Cargando más complementos…", "Đang tải thêm tiện ích…", "Memuat add-on lainnya…", "", "جارٍ تحميل المزيد من الإضافات…"),
        "FOLLOW THE JOURNEY" to row("SUBAYBAYAN ANG PAGLALAKBAY", "여정을 함께하세요", "活動をフォロー", "关注我们的旅程", "SIGUE EL VIAJE", "THEO DÕI HÀNH TRÌNH", "IKUTI PERJALANANNYA", "", "اتبع الرحلة"),
        "Open GitHub" to row("Buksan ang GitHub", "GitHub 열기", "GitHub を開く", "打开 GitHub", "Abrir GitHub", "Mở GitHub", "Buka GitHub", "", "افتح جيثب"),
        "Watch on YouTube" to row("Manood sa YouTube", "YouTube에서 보기", "YouTube で見る", "在 YouTube 上观看", "Ver en YouTube", "Xem trên YouTube", "Tonton di YouTube", "", "شاهد على يوتيوب"),
        "Explore the source and releases on GitHub, then follow Jester Mods on YouTube for videos and updates." to row("Tingnan ang source at releases sa GitHub, at sundan ang Jester Mods sa YouTube para sa mga video at update.", "GitHub에서 소스와 릴리스를 확인하고 YouTube에서 Jester Mods의 영상과 소식을 팔로우하세요.", "GitHub でソースとリリースを確認し、YouTube で Jester Mods の動画と更新をフォローしましょう。", "在 GitHub 上查看源码和发布版，并在 YouTube 上关注 Jester Mods 的视频和更新。", "Explora el código y las versiones en GitHub y sigue Jester Mods en YouTube para ver vídeos y novedades.", "Khám phá mã nguồn và bản phát hành trên GitHub, sau đó theo dõi Jester Mods trên YouTube để xem video và cập nhật.", "Jelajahi sumber dan rilis di GitHub, lalu ikuti Jester Mods di YouTube untuk video dan pembaruan.", "", "استكشف المصدر والإصدارات على GitHub، ثم تابع Jester Mods على YouTube للحصول على مقاطع الفيديو والتحديثات."),
        "View update" to row("Tingnan ang update", "업데이트 보기", "更新を見る", "查看更新", "Ver actualización", "Xem bản cập nhật", "Lihat pembaruan", "", "عرض التحديث"),
        "View full history" to row("Tingnan ang buong history", "전체 기록 보기", "すべての履歴を見る", "查看完整历史", "Ver historial completo", "Xem toàn bộ lịch sử", "Lihat riwayat lengkap", "", "عرض التاريخ الكامل"),
        "Preparing secure access" to row("Inihahanda ang secure access", "보안 접근 준비 중", "安全なアクセスを準備中", "正在准备安全访问", "Preparando acceso seguro", "Đang chuẩn bị quyền truy cập an toàn", "Menyiapkan akses aman", "", "إعداد الوصول الآمن"),
        "Restoring your launcher session and checking the device handoff." to row("Ibinabalik ang launcher session at sinusuri ang paglipat ng device.", "런처 세션을 복원하고 기기 전환을 확인하는 중입니다.", "ランチャーセッションを復元し、デバイス連携を確認中です。", "正在恢复启动器会话并检查设备交接。", "Restaurando la sesión y comprobando la entrega del dispositivo.", "Đang khôi phục phiên và kiểm tra chuyển giao thiết bị.", "Memulihkan sesi peluncur dan memeriksa serah terima perangkat.", "", "استعادة جلسة التشغيل الخاصة بك والتحقق من عملية تسليم الجهاز."),
        "Access is active" to row("Aktibo ang access", "접근이 활성화됨", "アクセスは有効です", "访问已激活", "El acceso está activo", "Quyền truy cập đang hoạt động", "Akses aktif", "", "الوصول نشط"),
        "Your launcher is ready to use" to row("Handa nang gamitin ang launcher", "런처를 사용할 준비가 되었습니다", "ランチャーを使用できます", "启动器已就绪", "El lanzador está listo", "Trình khởi chạy đã sẵn sàng", "Peluncur siap digunakan", "", "المشغل الخاص بك جاهز للاستخدام"),
        "Enter launcher  →" to row("Pumasok sa launcher  →", "런처 열기  →", "ランチャーを開く  →", "进入启动器  →", "Entrar al lanzador  →", "Mở trình khởi chạy  →", "Masuk ke peluncur  →", "", "أدخل المشغل →"),
        "TIME REMAINING" to row("NATITIRANG ORAS", "남은 시간", "残り時間", "剩余时间", "TIEMPO RESTANTE", "THỜI GIAN CÒN LẠI", "WAKTU TERSISA", "", "الوقت المتبقي"),
        "Available until" to row("Available hanggang", "사용 가능 기한", "利用可能期限", "可用至", "Disponible hasta", "Khả dụng đến", "Tersedia hingga", "", "متاح حتى"),
        "Active" to row("Aktibo", "활성", "有効", "已激活", "Activo", "Đang hoạt động", "Aktif", "", "نشط"),
        "Checking root access" to row("Sinusuri ang root access", "루트 권한 확인 중", "root アクセスを確認中", "正在检查 Root 权限", "Comprobando acceso root", "Đang kiểm tra quyền root", "Memeriksa akses root", "", "التحقق من الوصول إلى الجذر"),
        "Checking your digital key" to row("Sinusuri ang iyong digital key", "디지털 키 확인 중", "デジタルキーを確認中", "正在检查数字密钥", "Comprobando tu clave digital", "Đang kiểm tra khóa kỹ thuật số", "Memeriksa kunci digital Anda", "", "التحقق من المفتاح الرقمي الخاص بك"),
        "Root access is required" to row("Kailangan ang root access", "루트 권한이 필요합니다", "root アクセスが必要です", "需要 Root 权限", "Se requiere acceso root", "Cần quyền truy cập root", "Akses root diperlukan", "", "مطلوب الوصول إلى الجذر"),
        "Security check failed" to row("Nabigo ang security check", "보안 검사 실패", "セキュリティチェックに失敗しました", "安全检查失败", "Falló la comprobación de seguridad", "Kiểm tra bảo mật thất bại", "Pemeriksaan keamanan gagal", "", "فشل فحص الأمان"),
        "Connect to restore access" to row("Kumonekta upang ibalik ang access", "접근을 복원하려면 연결하세요", "アクセスを復元するには接続してください", "连接以恢复访问", "Conéctate para restaurar el acceso", "Kết nối để khôi phục quyền truy cập", "Hubungkan untuk memulihkan akses", "", "الاتصال لاستعادة الوصول"),
        "Unlock for 1 day" to row("I-unlock nang 1 araw", "1일 잠금 해제", "1日間アンロック", "解锁 1 天", "Desbloquear por 1 día", "Mở khóa trong 1 ngày", "Buka selama 1 hari", "", "فتح لمدة 1 يوم"),
        "This should only take a moment." to row("Sandali lang ito.", "잠시만 기다려 주세요.", "まもなく完了します。", "这只需要片刻。", "Esto solo tardará un momento.", "Quá trình này chỉ mất một lát.", "Ini hanya perlu waktu sebentar.", "", "هذا يجب أن يستغرق لحظة واحدة فقط."),
        "Continue to Linkvertise" to row("Magpatuloy sa Linkvertise", "Linkvertise로 계속", "Linkvertise に進む", "继续前往 Linkvertise", "Continuar a Linkvertise", "Tiếp tục đến Linkvertise", "Lanjutkan ke Linkvertise", "", "الاستمرار في Linkvertise"),
        "Copy support code" to row("Kopyahin ang support code", "지원 코드 복사", "サポートコードをコピー", "复制支持代码", "Copiar código de soporte", "Sao chép mã hỗ trợ", "Salin kode dukungan", "", "انسخ رمز الدعم"),
        "Everything personal, helpful, and new—kept in one refined space." to row("Lahat ng personal, kapaki-pakinabang, at bago—nasa iisang maayos na lugar.", "개인 정보와 도움말, 새 소식을 하나의 세련된 공간에서 확인하세요.", "個人情報、ヘルプ、新着情報を一つの洗練された場所に。", "个人信息、帮助和新内容，尽在一个精致空间。", "Todo lo personal, útil y nuevo en un espacio refinado.", "Mọi thứ cá nhân, hữu ích và mới mẻ trong một không gian tinh tế.", "Semua yang pribadi, berguna, dan baru dalam satu ruang elegan.", "", "كل شيء شخصي، ومفيد، وجديد — محفوظ في مكان واحد أنيق."),
        "All" to row("Lahat", "전체", "すべて", "全部", "Todos", "Tất cả", "Semua", "", "الكل"),
        "On device" to row("Nasa device", "기기에 있음", "デバイス上", "设备上", "En el dispositivo", "Trên thiết bị", "Di perangkat", "", "على الجهاز"),
        "Compatible" to row("Compatible", "호환됨", "互換", "兼容", "Compatible", "Tương thích", "Kompatibel", "", "متوافق"),
        "New" to row("Bago", "신규", "新着", "新内容", "Nuevo", "Mới", "Baru", "", "جديد"),
        "Recommended" to row("Inirerekomenda", "추천", "おすすめ", "推荐", "Recomendado", "Đề xuất", "Direkomendasikan", "", "موصى به"),
        "Recently updated" to row("Kamakailang na-update", "최근 업데이트", "最近更新", "最近更新", "Actualizados recientemente", "Mới cập nhật", "Baru diperbarui", "", "تم تحديثها مؤخرا"),
        "Most popular" to row("Pinakasikat", "인기순", "人気順", "最受欢迎", "Más populares", "Phổ biến nhất", "Terpopuler", "", "الأكثر شعبية"),
        "Ready" to row("Handa", "준비됨", "準備完了", "就绪", "Listo", "Sẵn sàng", "Siap", "", "جاهز"),
        "Update" to row("I-update", "업데이트", "更新", "更新", "Actualizar", "Cập nhật", "Perbarui", "", "تحديث"),
        "Check again" to row("Suriin muli", "다시 확인", "再確認", "再次检查", "Comprobar de nuevo", "Kiểm tra lại", "Periksa lagi", "", "تحقق مرة أخرى"),
        "Play" to row("Maglaro", "플레이", "プレイ", "开始游戏", "Jugar", "Chơi", "Main", "", "العب"),
        "Play again" to row("Maglaro muli", "다시 플레이", "もう一度プレイ", "再次游戏", "Jugar de nuevo", "Chơi lại", "Main lagi", "", "العب مرة أخرى"),
        "Resume" to row("Ipagpatuloy", "계속하기", "再開", "继续", "Reanudar", "Tiếp tục", "Lanjutkan", "", "استئناف"),
        "View requirements" to row("Tingnan ang requirements", "요구 사항 보기", "要件を見る", "查看要求", "Ver requisitos", "Xem yêu cầu", "Lihat persyaratan", "", "عرض المتطلبات"),
        "Patch & install" to row("I-patch at i-install", "패치 및 설치", "パッチしてインストール", "修补并安装", "Parchear e instalar", "Vá và cài đặt", "Patch & instal", "", "التصحيح والتثبيت"),
        "Update patched game" to row("I-update ang patched game", "패치된 게임 업데이트", "パッチ済みゲームを更新", "更新已修补游戏", "Actualizar juego parcheado", "Cập nhật trò chơi đã vá", "Perbarui game yang di-patch", "", "تحديث اللعبة المصححة"),
        "Restore official game" to row("Ibalik ang official game", "공식 게임 복원", "公式ゲームを復元", "恢复官方游戏", "Restaurar juego oficial", "Khôi phục trò chơi chính thức", "Pulihkan game resmi", "", "استعادة اللعبة الرسمية"),
        "Create & install shell" to row("Gumawa at mag-install ng shell", "셸 생성 및 설치", "シェルを作成してインストール", "创建并安装壳", "Crear e instalar shell", "Tạo và cài đặt shell", "Buat & instal shell", "", "إنشاء وتثبيت شل"),
        "Preparing shell…" to row("Inihahanda ang shell…", "셸 준비 중…", "シェルを準備中…", "正在准备壳…", "Preparando shell…", "Đang chuẩn bị shell…", "Menyiapkan shell…", "", "تحضير القشرة…"),
        "Checking migration…" to row("Sinusuri ang migration…", "마이그레이션 확인 중…", "移行を確認中…", "正在检查迁移…", "Comprobando migración…", "Đang kiểm tra chuyển đổi…", "Memeriksa migrasi…", "", "جارٍ التحقق من الهجرة..."),
        "Preparing patch…" to row("Inihahanda ang patch…", "패치 준비 중…", "パッチを準備中…", "正在准备补丁…", "Preparando parche…", "Đang chuẩn bị bản vá…", "Menyiapkan patch…", "", "جارٍ تحضير التصحيح…"),
        "Launching…" to row("Binubuksan…", "실행 중…", "起動中…", "正在启动…", "Iniciando…", "Đang khởi chạy…", "Meluncurkan…", "", "جارٍ الإطلاق…"),
        "A NEW ERA AWAITS" to row("ISANG BAGONG PANAHON ANG NAGHIHINTAY", "새로운 시대가 기다립니다", "新時代が待っています", "新时代正在等待", "UNA NUEVA ERA TE ESPERA", "MỘT KỶ NGUYÊN MỚI ĐANG CHỜ", "ERA BARU MENANTI", "", "عصر جديد في انتظارنا"),
        "MAKE IT YOURS" to row("GAWIN ITONG IYO", "나만의 스타일로", "自分らしく", "打造专属风格", "HAZLO TUYO", "BIẾN NÓ THÀNH CỦA BẠN", "JADIKAN MILIK ANDA", "", "اجعلها ملكك"),
        "SPEAK YOUR LANGUAGE" to row("GAMITIN ANG IYONG WIKA", "내 언어로 사용", "あなたの言語で", "使用您的语言", "HABLA TU IDIOMA", "DÙNG NGÔN NGỮ CỦA BẠN", "GUNAKAN BAHASA ANDA", "", "تحدث بلغتك"),
        "BUILT WITH PURPOSE" to row("GINAWA NANG MAY LAYUNIN", "목적을 담아 제작", "目的を持って設計", "为目标而打造", "CREADO CON PROPÓSITO", "ĐƯỢC TẠO RA CÓ MỤC ĐÍCH", "DIBUAT DENGAN TUJUAN", "", "بنيت مع الغرض"),
        "PREPARING YOUR EXPERIENCE" to row("INIHHAHANDA ANG IYONG EXPERIENCE", "환경 준비 중", "体験を準備中", "正在准备您的体验", "PREPARANDO TU EXPERIENCIA", "ĐANG CHUẨN BỊ TRẢI NGHIỆM", "MENYIAPKAN PENGALAMAN ANDA", "", "إعداد تجربتك"),
        "SECURE GAME COMPANION" to row("SECURE GAME COMPANION", "보안 게임 도우미", "安全なゲームコンパニオン", "安全游戏助手", "COMPAÑERO DE JUEGO SEGURO", "TRỢ LÝ TRÒ CHƠI AN TOÀN", "PENDAMPING GAME AMAN", "", "رفيق اللعبة الآمن"),
        "DEVICE SETUP" to row("SETUP NG DEVICE", "기기 설정", "デバイス設定", "设备设置", "CONFIGURACIÓN DEL DISPOSITIVO", "THIẾT LẬP THIẾT BỊ", "PENYIAPAN PERANGKAT", "", "إعداد الجهاز"),
        "GET THE GAME" to row("KUNIN ANG LARO", "게임 받기", "ゲームを入手", "获取游戏", "OBTENER EL JUEGO", "TẢI TRÒ CHƠI", "DAPATKAN GAME", "", "احصل على اللعبة"),
        "ADD ADD-ON" to row("IDAGDAG ANG ADD-ON", "애드온 추가", "アドオンを追加", "添加附加组件", "AÑADIR COMPLEMENTO", "THÊM TIỆN ÍCH", "TAMBAH ADD-ON", "", "إضافة وظيفة إضافية"),
        "LIBRARY UPDATES" to row("MGA UPDATE SA LIBRARY", "라이브러리 업데이트", "ライブラリ更新", "游戏库更新", "ACTUALIZACIONES DE BIBLIOTECA", "CẬP NHẬT THƯ VIỆN", "PEMBARUAN PUSTAKA", "", "تحديثات المكتبة"),
        "LATEST RELEASE" to row("PINAKABAGONG RELEASE", "최신 릴리스", "最新リリース", "最新版本", "ÚLTIMA VERSIÓN", "BẢN PHÁT HÀNH MỚI NHẤT", "RILIS TERBARU", "", "أحدث إصدار"),
        "CHANGES SINCE YOUR VERSION" to row("MGA PAGBABAGO MULA SA IYONG BERSYON", "현재 버전 이후 변경 사항", "現在のバージョン以降の変更", "自当前版本以来的更改", "CAMBIOS DESDE TU VERSIÓN", "THAY ĐỔI TỪ PHIÊN BẢN CỦA BẠN", "PERUBAHAN SEJAK VERSI ANDA", "", "التغييرات منذ الإصدار الخاص بك"),
        "PRIVATE ACCESS" to row("PRIVATE ACCESS", "비공개 액세스", "プライベートアクセス", "专属访问", "ACCESO PRIVADO", "QUYỀN TRUY CẬP RIÊNG", "AKSES PRIBADI", "", "وصول خاص"),
        "LIMITED ACCESS" to row("LIMITADONG ACCESS", "제한된 액세스", "限定アクセス", "有限访问", "ACCESO LIMITADO", "QUYỀN TRUY CẬP GIỚI HẠN", "AKSES TERBATAS", "", "وصول محدود"),
        "PUBLIC" to row("PAMPUBLIKO", "공개", "公開", "公开", "PÚBLICO", "CÔNG KHAI", "PUBLIK", "", "عام"),
        "Your support code" to row("Iyong support code", "지원 코드", "サポートコード", "您的支持代码", "Tu código de soporte", "Mã hỗ trợ của bạn", "Kode dukungan Anda", "", "رمز الدعم الخاص بك"),
        "One code for this phone, whether you use Root or Non-root." to row("Isang code para sa phone na ito, Root man o Non-root.", "Root 또는 Non-root 모두 이 휴대전화에 하나의 코드를 사용합니다.", "Root／Non-root にかかわらず、このスマートフォンには一つのコードを使用します。", "无论使用 Root 还是非 Root，此手机只需一个代码。", "Un código para este teléfono, uses Root o Non-root.", "Một mã cho điện thoại này, dù dùng Root hay Non-root.", "Satu kode untuk ponsel ini, baik Root maupun Non-root.", "", "رمز واحد لهذا الهاتف، سواء كنت تستخدم الجذر أو غير الجذر."),
        "Use this page when you need to identify this phone for access, recovery, or help." to row("Gamitin ang page na ito para makilala ang phone para sa access, recovery, o tulong.", "접근, 복구 또는 도움이 필요할 때 이 페이지에서 휴대전화를 식별하세요.", "アクセス、復旧、サポートでこの端末を識別する際にこのページを使います。", "需要访问、恢复或帮助时，使用此页面识别此手机。", "Usa esta página para identificar el teléfono al acceder, recuperar o pedir ayuda.", "Dùng trang này để nhận dạng điện thoại khi truy cập, khôi phục hoặc cần trợ giúp.", "Gunakan halaman ini untuk mengenali ponsel saat mengakses, memulihkan, atau meminta bantuan.", "", "استخدم هذه الصفحة عندما تحتاج إلى تحديد هذا الهاتف للوصول إليه أو استرداده أو المساعدة."),
        "Launcher releases and add-on update history." to row("Mga launcher release at update history ng add-on.", "런처 릴리스 및 애드온 업데이트 기록.", "ランチャーのリリースとアドオン更新履歴。", "启动器版本和附加组件更新历史。", "Versiones del lanzador e historial de complementos.", "Bản phát hành trình khởi chạy và lịch sử cập nhật tiện ích.", "Rilis peluncur dan riwayat pembaruan add-on.", "", "إصدارات المشغل وسجل تحديث الوظائف الإضافية."),
        "RELEASE JOURNAL" to row("TALAAN NG MGA RELEASE", "릴리스 저널", "リリースジャーナル", "发布日志", "DIARIO DE LANZAMIENTOS", "NHẬT KÝ PHÁT HÀNH", "JURNAL RILIS", "", "إصدار المجلة"),
        "LAUNCHER" to row("LAUNCHER", "런처", "ランチャー", "启动器", "LANZADOR", "TRÌNH KHỞI CHẠY", "PELUNCUR", "", "قاذفة"),
        "The launcher, refined release by release." to row("Ang launcher, pinapahusay sa bawat release.", "릴리스마다 더 다듬어지는 런처입니다.", "リリースごとに磨かれるランチャー。", "启动器随每次发布不断完善。", "El lanzador, perfeccionado versión tras versión.", "Trình khởi chạy được hoàn thiện qua từng bản phát hành.", "Peluncur yang disempurnakan di setiap rilis.", "", "قاذفة، الإصدار المكرر من قبل الإصدار."),
        "Every supported game has its own story." to row("Bawat supported na laro ay may sariling kuwento.", "지원되는 모든 게임에는 고유한 이야기가 있습니다.", "対応ゲームごとに、それぞれの物語があります。", "每款受支持的游戏都有自己的故事。", "Cada juego compatible tiene su propia historia.", "Mỗi trò chơi được hỗ trợ đều có câu chuyện riêng.", "Setiap game yang didukung memiliki kisahnya sendiri.", "", "كل لعبة مدعومة لها قصتها الخاصة."),
        "JESTER MODS ARCHIVE" to row("JESTER MODS ARCHIVE", "JESTER MODS 아카이브", "JESTER MODS アーカイブ", "JESTER MODS 档案", "ARCHIVO DE JESTER MODS", "KHO LƯU TRỮ JESTER MODS", "ARSIP JESTER MODS", "", "أرشيف تعديل المهرج"),
        "ADD-ON ARCHIVE" to row("ADD-ON ARCHIVE", "애드온 아카이브", "アドオンアーカイブ", "附加组件档案", "ARCHIVO DE COMPLEMENTOS", "KHO LƯU TRỮ TIỆN ÍCH", "ARSIP ADD-ON", "", "أرشيف الإضافات"),
        "Release history" to row("History ng release", "릴리스 기록", "リリース履歴", "发布历史", "Historial de lanzamientos", "Lịch sử phát hành", "Riwayat rilis", "", "تاريخ الإصدار"),
        "RELEASES" to row("MGA RELEASE", "릴리스", "リリース", "发布", "LANZAMIENTOS", "BẢN PHÁT HÀNH", "RILIS", "", "الإصدارات"),
        "Search update activity" to row("Maghanap sa update activity", "업데이트 활동 검색", "更新履歴を検索", "搜索更新活动", "Buscar actividad de actualización", "Tìm hoạt động cập nhật", "Cari aktivitas pembaruan", "", "نشاط تحديث البحث"),
        "Every verified Jester Mods release." to row("Bawat verified na Jester Mods release.", "검증된 모든 Jester Mods 릴리스.", "検証済みのすべての Jester Mods リリース。", "所有经过验证的 Jester Mods 版本。", "Cada versión verificada de Jester Mods.", "Mọi bản phát hành Jester Mods đã xác minh.", "Setiap rilis Jester Mods yang terverifikasi.", "", "كل إصدار تم التحقق منه من Jester Mods."),
        "Launcher history" to row("History ng launcher", "런처 기록", "ランチャー履歴", "启动器历史", "Historial del lanzador", "Lịch sử trình khởi chạy", "Riwayat peluncur", "", "تاريخ قاذفة"),
        "Refreshing verified changelogs…" to row("Nire-refresh ang verified changelogs…", "검증된 변경 내역 새로고침 중…", "検証済み変更履歴を更新中…", "正在刷新已验证的更新日志…", "Actualizando registros verificados…", "Đang làm mới nhật ký đã xác minh…", "Menyegarkan catatan perubahan terverifikasi…", "", "جارٍ تحديث سجلات التغيير التي تم التحقق منها..."),
        "Try launcher history again" to row("Subukan muli ang launcher history", "런처 기록 다시 시도", "ランチャー履歴を再試行", "重试启动器历史", "Reintentar historial del lanzador", "Thử lại lịch sử trình khởi chạy", "Coba lagi riwayat peluncur", "", "حاول سجل المشغل مرة أخرى"),
        "Copy diagnostics" to row("Kopyahin ang diagnostics", "진단 복사", "診断をコピー", "复制诊断信息", "Copiar diagnósticos", "Sao chép chẩn đoán", "Salin diagnostik", "", "نسخ التشخيص"),
        "Verified package" to row("Verified package", "검증된 패키지", "検証済みパッケージ", "已验证的软件包", "Paquete verificado", "Gói đã xác minh", "Paket terverifikasi", "", "الحزمة التي تم التحقق منها"),
        "Check for the latest version before you play." to row("Suriin ang pinakabagong bersyon bago maglaro.", "플레이 전에 최신 버전을 확인하세요.", "プレイ前に最新バージョンを確認してください。", "游戏前请检查最新版本。", "Busca la última versión antes de jugar.", "Kiểm tra phiên bản mới nhất trước khi chơi.", "Periksa versi terbaru sebelum bermain.", "", "تحقق من وجود أحدث إصدار قبل اللعب."),
        "This device isn't supported" to row("Hindi supported ang device na ito", "이 기기는 지원되지 않습니다", "このデバイスはサポートされていません", "不支持此设备", "Este dispositivo no es compatible", "Thiết bị này không được hỗ trợ", "Perangkat ini tidak didukung", "", "هذا الجهاز غير مدعوم"),
        "This device version isn't supported" to row("Hindi supported ang version ng device na ito", "이 기기 버전은 지원되지 않습니다", "このデバイスのバージョンはサポートされていません", "不支持此设备版本", "Esta versión del dispositivo no es compatible", "Phiên bản thiết bị này không được hỗ trợ", "Versi perangkat ini tidak didukung", "", "إصدار الجهاز هذا غير مدعوم"),
        "No add-ons match these filters" to row("Walang add-on na tugma sa filters", "필터와 일치하는 애드온이 없습니다", "フィルターに一致するアドオンはありません", "没有符合筛选条件的附加组件", "Ningún complemento coincide con los filtros", "Không có tiện ích khớp bộ lọc", "Tidak ada add-on yang cocok dengan filter", "", "لا توجد إضافات تتطابق مع هذه المرشحات"),
        "Try another search, category, or filter." to row("Subukan ang ibang search, category, o filter.", "다른 검색어, 카테고리 또는 필터를 사용해 보세요.", "別の検索、カテゴリ、フィルターを試してください。", "请尝试其他搜索、类别或筛选条件。", "Prueba otra búsqueda, categoría o filtro.", "Thử tìm kiếm, danh mục hoặc bộ lọc khác.", "Coba pencarian, kategori, atau filter lain.", "", "جرّب بحثًا أو فئة أو عامل تصفية آخر."),
        "Clear filters" to row("I-clear ang filters", "필터 지우기", "フィルターをクリア", "清除筛选", "Borrar filtros", "Xóa bộ lọc", "Hapus filter", "", "مسح المرشحات"),
        "Try another game name or package." to row("Subukan ang ibang pangalan o package ng laro.", "다른 게임 이름이나 패키지를 사용해 보세요.", "別のゲーム名またはパッケージを試してください。", "请尝试其他游戏名称或包名。", "Prueba otro nombre o paquete de juego.", "Thử tên trò chơi hoặc gói khác.", "Coba nama game atau paket lain.", "", "جرب اسمًا أو حزمة لعبة أخرى."),
        "All available add-ons are already installed. You can find them in your library." to row("Naka-install na ang lahat ng available na add-on. Makikita sila sa library.", "사용 가능한 모든 애드온이 설치되어 있습니다. 라이브러리에서 확인하세요.", "利用可能なアドオンはすべてインストール済みです。ライブラリで確認できます。", "所有可用附加组件均已安装，可在游戏库中查看。", "Todos los complementos disponibles ya están instalados. Están en tu biblioteca.", "Tất cả tiện ích khả dụng đã được cài đặt. Bạn có thể tìm thấy trong thư viện.", "Semua add-on yang tersedia sudah terinstal. Temukan di pustaka.", "", "جميع الوظائف الإضافية المتاحة مثبتة بالفعل. يمكنك العثور عليها في مكتبتك."),
        "Choose an add-on for a supported game, then add it to your Jester Mods library." to row("Pumili ng add-on para sa supported game at idagdag sa Jester Mods library.", "지원되는 게임의 애드온을 선택해 Jester Mods 라이브러리에 추가하세요.", "対応ゲームのアドオンを選び、Jester Mods ライブラリに追加してください。", "为受支持的游戏选择附加组件，然后添加到 Jester Mods 游戏库。", "Elige un complemento para un juego compatible y añádelo a tu biblioteca de Jester Mods.", "Chọn tiện ích cho trò chơi được hỗ trợ rồi thêm vào thư viện Jester Mods.", "Pilih add-on untuk game yang didukung lalu tambahkan ke pustaka Jester Mods.", "", "اختر وظيفة إضافية للعبة مدعومة، ثم قم بإضافتها إلى مكتبة Jester Mods الخاصة بك."),
        "Pull down to refresh your library and the add-on catalog." to row("Hilahin pababa para i-refresh ang library at add-on catalog.", "아래로 당겨 라이브러리와 애드온 카탈로그를 새로고침하세요.", "下に引いてライブラリとアドオンカタログを更新します。", "下拉刷新游戏库和附加组件目录。", "Desliza hacia abajo para actualizar la biblioteca y el catálogo.", "Kéo xuống để làm mới thư viện và danh mục tiện ích.", "Tarik ke bawah untuk menyegarkan pustaka dan katalog add-on.", "", "اسحب للأسفل لتحديث مكتبتك وكتالوج الوظائف الإضافية."),
        "Checking your installed add-ons and supported games…" to row("Sinusuri ang installed add-ons at supported games…", "설치된 애드온과 지원 게임 확인 중…", "インストール済みアドオンと対応ゲームを確認中…", "正在检查已安装的附加组件和支持的游戏…", "Comprobando complementos instalados y juegos compatibles…", "Đang kiểm tra tiện ích đã cài và trò chơi được hỗ trợ…", "Memeriksa add-on terinstal dan game yang didukung…", "", "جارٍ التحقق من الوظائف الإضافية المثبتة والألعاب المدعومة..."),
        "Keep this add-on ready for the game." to row("Panatilihing handa ang add-on para sa laro.", "게임용 애드온을 준비 상태로 유지하세요.", "ゲーム用アドオンを準備しておきます。", "让此附加组件随时可用于游戏。", "Mantén este complemento listo para el juego.", "Giữ tiện ích này sẵn sàng cho trò chơi.", "Jaga add-on ini tetap siap untuk game.", "", "احتفظ بهذه الوظيفة الإضافية جاهزة للعبة."),
        "Jester Mods will download the add-on for this installed game." to row("Ida-download ng Jester Mods ang add-on para sa installed game.", "Jester Mods가 설치된 게임용 애드온을 다운로드합니다.", "Jester Mods がインストール済みゲーム用アドオンをダウンロードします。", "Jester Mods 将为已安装的游戏下载附加组件。", "Jester Mods descargará el complemento para este juego instalado.", "Jester Mods sẽ tải tiện ích cho trò chơi đã cài đặt.", "Jester Mods akan mengunduh add-on untuk game terinstal ini.", "", "سيقوم Jester Mods بتنزيل الوظيفة الإضافية لهذه اللعبة المثبتة."),
        "Open Google Play instead" to row("Buksan na lang ang Google Play", "대신 Google Play 열기", "代わりに Google Play を開く", "改为打开 Google Play", "Abrir Google Play en su lugar", "Mở Google Play thay thế", "Buka Google Play sebagai gantinya", "", "افتح Google Play بدلاً من ذلك"),
        "The available download is not newer than your installed game." to row("Hindi mas bago ang available download kaysa installed game.", "사용 가능한 다운로드가 설치된 게임보다 최신이 아닙니다.", "利用可能なダウンロードはインストール済みゲームより新しくありません。", "可用下载并不比已安装的游戏更新。", "La descarga disponible no es más reciente que el juego instalado.", "Bản tải xuống không mới hơn trò chơi đã cài.", "Unduhan yang tersedia tidak lebih baru dari game terinstal.", "", "التنزيل المتاح ليس أحدث من لعبتك المثبتة."),
        "Stay connected to Jester Mods" to row("Manatiling konektado sa Jester Mods", "Jester Mods와 계속 소통하세요", "Jester Mods とつながる", "关注 Jester Mods", "Sigue conectado con Jester Mods", "Luôn kết nối với Jester Mods", "Tetap terhubung dengan Jester Mods", "", "ابق على اتصال بـ Jester Mods"),
        "A curated home for game add-ons—designed to make discovery, compatibility, updates, and play feel effortless." to row("Isang piniling tahanan ng game add-ons—madaling maghanap, magsuri, mag-update, at maglaro.", "게임 애드온을 엄선한 공간—탐색, 호환성, 업데이트, 플레이를 간편하게.", "ゲームアドオンを厳選した場所—発見、互換性、更新、プレイを簡単に。", "精心打造的游戏附加组件之家，让发现、兼容、更新和游玩更轻松。", "Un espacio selecto para complementos que facilita descubrir, comprobar, actualizar y jugar.", "Không gian tuyển chọn tiện ích trò chơi giúp khám phá, tương thích, cập nhật và chơi dễ dàng.", "Rumah pilihan add-on game agar penemuan, kompatibilitas, pembaruan, dan bermain terasa mudah.", "", "منزل منظم للوظائف الإضافية للألعاب - مصمم لتسهيل الاكتشاف والتوافق والتحديثات واللعب."),
        "Made for players who want more from the games they love." to row("Ginawa para sa players na gusto pa ng higit sa mga paboritong laro.", "좋아하는 게임을 더 즐기고 싶은 플레이어를 위해 만들었습니다.", "好きなゲームをもっと楽しみたいプレイヤーのために。", "为希望从喜爱的游戏中获得更多乐趣的玩家而打造。", "Creado para jugadores que quieren más de sus juegos favoritos.", "Dành cho người chơi muốn nhiều hơn từ trò chơi mình yêu thích.", "Dibuat untuk pemain yang ingin lebih dari game favoritnya.", "", "مصمم للاعبين الذين يريدون المزيد من الألعاب التي يحبونها."),
        "Browse available add-ons and add them to supported games. Library entries stay here even if the original game later needs to be reinstalled." to row("Tingnan ang add-ons at idagdag sa supported games. Mananatili sa Library kahit kailangang i-install muli ang original game.", "애드온을 찾아 지원 게임에 추가하세요. 원본 게임을 다시 설치해도 라이브러리 항목은 유지됩니다.", "アドオンを探して対応ゲームに追加します。元のゲームを再インストールしてもライブラリ項目は残ります。", "浏览附加组件并添加到支持的游戏。即使之后重装原版游戏，游戏库条目仍会保留。", "Explora complementos y añádelos a juegos compatibles. Seguirán en la biblioteca aunque reinstales el juego original.", "Duyệt tiện ích và thêm vào trò chơi được hỗ trợ. Mục thư viện vẫn còn nếu phải cài lại trò chơi gốc.", "Jelajahi add-on dan tambahkan ke game yang didukung. Entri pustaka tetap ada meski game asli perlu diinstal ulang.", "", "تصفح الوظائف الإضافية المتاحة وأضفها إلى الألعاب المدعومة. تظل إدخالات المكتبة هنا حتى إذا كانت اللعبة الأصلية تحتاج إلى إعادة تثبيتها لاحقًا."),
        "Search to narrow the list, then select one or many add-ons. Games and save data stay installed." to row("Mag-search para paliitin ang listahan, saka pumili ng add-on. Mananatiling installed ang games at save data.", "검색으로 목록을 좁힌 뒤 애드온을 선택하세요. 게임과 저장 데이터는 유지됩니다.", "検索で絞り込み、アドオンを選択します。ゲームとセーブデータは残ります。", "搜索以缩小列表，然后选择一个或多个附加组件。游戏和存档数据会保留。", "Busca para reducir la lista y selecciona complementos. Los juegos y datos guardados permanecen instalados.", "Tìm kiếm để thu hẹp danh sách rồi chọn tiện ích. Trò chơi và dữ liệu lưu vẫn được giữ.", "Cari untuk mempersempit daftar lalu pilih add-on. Game dan data simpanan tetap terinstal.", "", "ابحث لتضييق القائمة، ثم حدد وظيفة إضافية واحدة أو أكثر. تظل الألعاب وحفظ البيانات مثبتة."),
        "The next chapter of Jester Mods is ready. Update securely without leaving the launcher." to row("Handa na ang susunod na kabanata ng Jester Mods. Mag-update nang secure sa launcher.", "Jester Mods의 다음 장이 준비되었습니다. 런처를 벗어나지 않고 안전하게 업데이트하세요.", "Jester Mods の次章が準備できました。ランチャー内で安全に更新できます。", "Jester Mods 的新篇章已就绪。无需离开启动器即可安全更新。", "El próximo capítulo de Jester Mods está listo. Actualiza de forma segura sin salir del lanzador.", "Chương tiếp theo của Jester Mods đã sẵn sàng. Cập nhật an toàn ngay trong trình khởi chạy.", "Babak berikutnya Jester Mods siap. Perbarui dengan aman tanpa keluar dari peluncur.", "", "الفصل التالي من Jester Mods جاهز. قم بالتحديث بشكل آمن دون مغادرة المشغّل."),
        "Android will ask you to confirm replacing this launcher. Your games, add-ons, and one-day access stay on this device." to row("Hihingi ang Android ng kumpirmasyon para palitan ang launcher. Mananatili ang games, add-ons, at one-day access.", "Android가 런처 교체 확인을 요청합니다. 게임, 애드온, 1일 접근 권한은 기기에 유지됩니다.", "Android がランチャー置換の確認を求めます。ゲーム、アドオン、1日アクセスは端末に残ります。", "Android 会要求确认替换启动器。游戏、附加组件和一天访问权限会保留在设备上。", "Android pedirá confirmar el reemplazo. Tus juegos, complementos y acceso de un día permanecen en el dispositivo.", "Android sẽ yêu cầu xác nhận thay thế. Trò chơi, tiện ích và quyền truy cập một ngày vẫn còn trên thiết bị.", "Android akan meminta konfirmasi penggantian. Game, add-on, dan akses satu hari tetap di perangkat.", "", "سيطلب منك Android تأكيد استبدال هذا المشغل. تبقى ألعابك ووظائفك الإضافية وإمكانية الوصول ليوم واحد على هذا الجهاز."),
        "Android owns the current confirmation. Jester Mods will verify the result when you return." to row("Android ang may hawak ng kumpirmasyon. Ibe-verify ng Jester Mods ang resulta pagbalik mo.", "현재 확인은 Android에서 진행됩니다. 돌아오면 Jester Mods가 결과를 확인합니다.", "現在の確認は Android が行います。戻ると Jester Mods が結果を確認します。", "当前确认由 Android 处理。返回后 Jester Mods 将验证结果。", "Android controla la confirmación actual. Jester Mods verificará el resultado cuando vuelvas.", "Android đang xử lý xác nhận. Jester Mods sẽ xác minh kết quả khi bạn quay lại.", "Konfirmasi saat ini ditangani Android. Jester Mods akan memverifikasi hasil saat Anda kembali.", "", "يمتلك Android التأكيد الحالي. سوف يقوم Jester Mods بالتحقق من النتيجة عند عودتك."),
        "Signed and verified by Jester Mods. Your library, add-ons, and access remain on this device." to row("Signed at verified ng Jester Mods. Mananatili sa device ang library, add-ons, at access.", "Jester Mods가 서명하고 검증했습니다. 라이브러리, 애드온, 접근 권한은 기기에 유지됩니다.", "Jester Mods による署名・検証済み。ライブラリ、アドオン、アクセスは端末に残ります。", "由 Jester Mods 签名并验证。游戏库、附加组件和访问权限会保留在此设备上。", "Firmado y verificado por Jester Mods. Tu biblioteca, complementos y acceso permanecen en el dispositivo.", "Được Jester Mods ký và xác minh. Thư viện, tiện ích và quyền truy cập vẫn ở trên thiết bị.", "Ditandatangani dan diverifikasi Jester Mods. Pustaka, add-on, dan akses tetap di perangkat.", "", "تم التوقيع والتحقق منها بواسطة Jester Mods. تظل مكتبتك ووظائفك الإضافية وإمكانية الوصول موجودة على هذا الجهاز."),
        "The installed game architecture does not match the available Jester Mods add-on." to row("Hindi tugma ang architecture ng installed game sa available na Jester Mods add-on.", "설치된 게임 아키텍처가 사용 가능한 Jester Mods 애드온과 일치하지 않습니다.", "インストール済みゲームのアーキテクチャが利用可能なアドオンと一致しません。", "已安装游戏的架构与可用的 Jester Mods 附加组件不匹配。", "La arquitectura del juego instalado no coincide con el complemento disponible.", "Kiến trúc trò chơi đã cài không khớp với tiện ích Jester Mods hiện có.", "Arsitektur game terinstal tidak cocok dengan add-on Jester Mods yang tersedia.", "", "لا تتطابق بنية اللعبة المثبتة مع الوظيفة الإضافية Jester Mods المتوفرة."),
        "The patched APK will never be stored inside the new shell as an original game payload." to row("Hindi kailanman ise-save ang patched APK sa bagong shell bilang original game payload.", "패치된 APK는 새 셸 안에 원본 게임 페이로드로 저장되지 않습니다.", "パッチ済み APK が新しいシェル内に元ゲームとして保存されることはありません。", "已修补的 APK 绝不会作为原版游戏载荷存入新壳中。", "El APK parcheado nunca se guardará dentro del nuevo shell como juego original.", "APK đã vá sẽ không bao giờ được lưu trong shell mới dưới dạng trò chơi gốc.", "APK yang di-patch tidak akan disimpan dalam shell baru sebagai game asli.", "", "لن يتم أبدًا تخزين ملف APK المصحح داخل الغلاف الجديد كحمولة أصلية للعبة."),
        "A previous Jester-patched installation was detected" to row("May nakitang dating Jester-patched installation", "이전 Jester 패치 설치가 감지되었습니다", "以前の Jester パッチ済みインストールを検出しました", "检测到以前的 Jester 修补安装", "Se detectó una instalación anterior parcheada por Jester", "Đã phát hiện bản cài đặt Jester đã vá trước đó", "Terdeteksi instalasi lama yang di-patch Jester", "", "تم الكشف عن تثبيت سابق لـ Jester"),
        "1. Continue to the Jester Mods website\n2. Complete Linkvertise and the browser check\n3. Tap Open launcher on the website" to row("1. Pumunta sa Jester Mods website\n2. Kumpletuhin ang Linkvertise at browser check\n3. I-tap ang Open launcher sa website", "1. Jester Mods 웹사이트로 이동\n2. Linkvertise와 브라우저 확인 완료\n3. 웹사이트에서 런처 열기 누르기", "1. Jester Mods サイトへ進む\n2. Linkvertise とブラウザ確認を完了\n3. サイトでランチャーを開くをタップ", "1. 前往 Jester Mods 网站\n2. 完成 Linkvertise 和浏览器检查\n3. 在网站上点击打开启动器", "1. Continúa al sitio de Jester Mods\n2. Completa Linkvertise y la comprobación\n3. Pulsa Abrir lanzador en el sitio", "1. Tiếp tục đến trang Jester Mods\n2. Hoàn tất Linkvertise và kiểm tra trình duyệt\n3. Nhấn Mở trình khởi chạy trên trang", "1. Lanjutkan ke situs Jester Mods\n2. Selesaikan Linkvertise dan pemeriksaan browser\n3. Ketuk Buka peluncur di situs", "", "1. تابع إلى موقع Jester Mods\n2. أكمل Linkvertise وفحص المتصفح\n3. اضغط على فتح المشغل على موقع الويب"),
        "Midnight" to row("Hatinggabi", "미드나이트", "ミッドナイト", "午夜", "Medianoche", "Nửa đêm", "Tengah Malam", "", "منتصف الليل"),
        "Aurora" to row("Aurora", "오로라", "オーロラ", "极光", "Aurora", "Cực quang", "Aurora", "", "أورورا"),
        "Royal" to row("Maharlika", "로열", "ロイヤル", "皇家", "Real", "Hoàng gia", "Royal", "", "رويال"),
        "Ember" to row("Baga", "불씨", "残り火", "余烬", "Ascua", "Than hồng", "Bara", "", "جمرة"),
        "Ocean" to row("Karagatan", "오션", "オーシャン", "海洋", "Océano", "Đại dương", "Samudra", "", "المحيط"),
        "Sakura" to row("Sakura", "사쿠라", "桜", "樱花", "Sakura", "Hoa anh đào", "Sakura", "", "ساكورا"),
        "Obsidian" to row("Obsidian", "옵시디언", "オブシディアン", "黑曜石", "Obsidiana", "Hắc diện thạch", "Obsidian", "", "سبج"),
        "Curated experiences" to row("Piniling experiences", "엄선된 경험", "厳選された体験", "精选体验", "Experiencias seleccionadas", "Trải nghiệm tuyển chọn", "Pengalaman pilihan", "", "تجارب منسقة"),
        "Browse focused add-ons with clear features and compatibility before entering a game." to row("Tingnan ang add-ons na may malinaw na features at compatibility bago pumasok sa laro.", "게임 실행 전에 기능과 호환성이 명확한 애드온을 확인하세요.", "ゲーム開始前に機能と互換性が明確なアドオンを確認できます。", "进入游戏前浏览功能和兼容性清晰的附加组件。", "Explora complementos con funciones y compatibilidad claras antes de entrar al juego.", "Duyệt tiện ích với tính năng và khả năng tương thích rõ ràng trước khi vào trò chơi.", "Jelajahi add-on dengan fitur dan kompatibilitas yang jelas sebelum masuk game.", "", "تصفح الوظائف الإضافية المركزة ذات الميزات الواضحة والتوافق قبل الدخول إلى اللعبة."),
        "Root and Non-root" to row("Root at Non-root", "Root 및 Non-root", "Root と Non-root", "Root 和非 Root", "Root y Non-root", "Root và Non-root", "Root dan Non-root", "", "الجذر وغير الجذر"),
        "Purpose-built launcher editions deliver the right method for each supported device setup." to row("Ang launcher editions ay nagbibigay ng tamang method para sa bawat supported device setup.", "각 런처 에디션은 지원되는 기기 설정에 맞는 방식을 제공합니다.", "各ランチャー版が対応デバイスに適した方法を提供します。", "不同启动器版本为每种受支持的设备设置提供正确方式。", "Las ediciones del lanzador ofrecen el método adecuado para cada dispositivo compatible.", "Các phiên bản trình khởi chạy cung cấp phương thức phù hợp cho từng thiết bị được hỗ trợ.", "Edisi peluncur menyediakan metode tepat untuk setiap perangkat yang didukung.", "", "توفر إصدارات Launcher المصممة خصيصًا الطريقة الصحيحة لكل إعداد جهاز مدعوم."),
        "Verified delivery" to row("Verified delivery", "검증된 배포", "検証済み配信", "验证交付", "Entrega verificada", "Phân phối đã xác minh", "Pengiriman terverifikasi", "", "التسليم المؤكد"),
        "Launcher updates, catalogs, and add-on files are checked against trusted release metadata." to row("Sinusuri ang updates, catalogs, at add-on files gamit ang trusted release metadata.", "런처 업데이트, 카탈로그, 애드온 파일을 신뢰할 수 있는 릴리스 정보와 대조합니다.", "更新、カタログ、アドオンファイルを信頼済みリリース情報と照合します。", "启动器更新、目录和附加组件文件会根据可信发布元数据进行检查。", "Las actualizaciones, catálogos y archivos se comprueban con metadatos de confianza.", "Bản cập nhật, danh mục và tệp tiện ích được kiểm tra bằng dữ liệu phát hành đáng tin cậy.", "Pembaruan, katalog, dan file add-on diperiksa dengan metadata rilis tepercaya.", "", "يتم فحص تحديثات المشغّل والكتالوجات وملفات الوظائف الإضافية مقابل بيانات تعريف الإصدار الموثوقة."),
        "Code to send" to row("Code na ipapadala", "보낼 코드", "送信するコード", "要发送的代码", "Código para enviar", "Mã để gửi", "Kode untuk dikirim", "", "رمز للإرسال"),
        "Copy code" to row("Kopyahin ang code", "코드 복사", "コードをコピー", "复制代码", "Copiar código", "Sao chép mã", "Salin kode", "", "نسخ الرمز"),
        "WHAT THIS CODE DOES" to row("ANO ANG GINAGAWA NG CODE", "이 코드의 기능", "このコードの役割", "此代码的作用", "QUÉ HACE ESTE CÓDIGO", "MÃ NÀY LÀM GÌ", "FUNGSI KODE INI", "", "ماذا يفعل هذا الرمز؟"),
        "DEVICE DETAILS" to row("DETALYE NG DEVICE", "기기 세부 정보", "デバイス詳細", "设备详情", "DETALLES DEL DISPOSITIVO", "CHI TIẾT THIẾT BỊ", "DETAIL PERANGKAT", "", "تفاصيل الجهاز"),
        "Phone code" to row("Phone code", "휴대전화 코드", "端末コード", "手机代码", "Código del teléfono", "Mã điện thoại", "Kode ponsel", "", "رمز الهاتف"),
        "Restore code" to row("Restore code", "복구 코드", "復元コード", "恢复代码", "Código de restauración", "Mã khôi phục", "Kode pemulihan", "", "استعادة الرمز"),
        "App security code" to row("App security code", "앱 보안 코드", "アプリセキュリティコード", "应用安全代码", "Código de seguridad", "Mã bảo mật ứng dụng", "Kode keamanan aplikasi", "", "رمز أمان التطبيق"),
        "Launcher type" to row("Uri ng launcher", "런처 유형", "ランチャー種別", "启动器类型", "Tipo de lanzador", "Loại trình khởi chạy", "Jenis peluncur", "", "نوع قاذفة"),
        "Install code" to row("Install code", "설치 코드", "インストールコード", "安装代码", "Código de instalación", "Mã cài đặt", "Kode instalasi", "", "رمز التثبيت"),
        "Unavailable" to row("Hindi available", "사용 불가", "利用不可", "不可用", "No disponible", "Không khả dụng", "Tidak tersedia", "", "غير متاح"),
        "Root edition" to row("Root edition", "Root 에디션", "Root 版", "Root 版", "Edición Root", "Bản Root", "Edisi Root", "", "الطبعة الجذرية"),
        "Non-root edition" to row("Non-root edition", "Non-root 에디션", "Non-root 版", "非 Root 版", "Edición Non-root", "Bản Non-root", "Edisi Non-root", "", "طبعة غير الجذر"),
        "Jester Mods signature teal in a deep, focused night." to row("Signature teal ng Jester Mods sa malalim at tahimik na gabi.", "깊고 차분한 밤에 빛나는 Jester Mods 시그니처 청록색.", "深く静かな夜に映える Jester Mods のシグネチャーティール。", "Jester Mods 标志性青绿色，置于深邃专注的夜色中。", "El turquesa característico de Jester Mods en una noche profunda.", "Màu xanh ngọc đặc trưng của Jester Mods trong màn đêm sâu lắng.", "Teal khas Jester Mods dalam malam yang dalam dan fokus.", "", "اللون الأزرق المخضر المميز من Jester Mods في ليلة عميقة ومركزة."),
        "Cool cyan and vivid green over a quiet northern sky." to row("Malamig na cyan at matingkad na berde sa tahimik na hilagang langit.", "고요한 북쪽 하늘 위의 시원한 시안과 선명한 초록.", "静かな北の空に広がる涼やかなシアンと鮮やかな緑。", "宁静北方天空下的冷青色与鲜绿色。", "Cian frío y verde intenso sobre un tranquilo cielo del norte.", "Xanh lơ mát và xanh lá rực rỡ trên bầu trời phương bắc yên tĩnh.", "Sian sejuk dan hijau cerah di langit utara yang tenang.", "", "سماوي بارد وأخضر حيوي فوق سماء شمالية هادئة."),
        "Luminous violet and gold with a refined midnight finish." to row("Maliwanag na violet at ginto na may eleganteng midnight finish.", "세련된 한밤의 마감 위에 빛나는 보라와 금색.", "洗練された真夜中に輝く紫と金。", "明亮紫色与金色，配以精致午夜质感。", "Violeta luminoso y dorado con un elegante acabado nocturno.", "Tím sáng và vàng trên nền nửa đêm tinh tế.", "Ungu bercahaya dan emas dengan sentuhan tengah malam elegan.", "", "البنفسجي المضيء والذهبي مع لمسة نهائية مصقولة في منتصف الليل."),
        "Warm amber and coral glowing against smoked obsidian." to row("Mainit na amber at coral na kumikinang sa smoked obsidian.", "그을린 흑요석 위로 빛나는 따뜻한 호박색과 산호색.", "燻した黒曜石に輝く暖かなアンバーとコーラル。", "温暖琥珀色与珊瑚色在烟熏黑曜石上闪耀。", "Ámbar cálido y coral brillante sobre obsidiana ahumada.", "Hổ phách ấm và san hô rực sáng trên nền hắc diện thạch.", "Amber hangat dan koral berkilau di atas obsidian berasap.", "", "العنبر الدافئ والمرجان يتوهجان ضد حجر السج المدخن."),
        "Crystal cyan and cobalt drifting through the deep blue." to row("Crystal cyan at cobalt na dumadaloy sa malalim na asul.", "깊은 파랑 속을 흐르는 수정빛 시안과 코발트.", "深い青を漂うクリスタルシアンとコバルト。", "水晶青与钴蓝在深蓝中流动。", "Cian cristalino y cobalto flotando en azul profundo.", "Xanh lơ pha lê và coban trôi trong sắc xanh thẳm.", "Sian kristal dan kobalt mengalir dalam biru pekat.", "", "الكريستال السماوي والكوبالت ينجرفان عبر اللون الأزرق العميق."),
        "Soft rose and lilac blooming over a rich plum night." to row("Malambot na rose at lilac na namumulaklak sa plum na gabi.", "짙은 자두빛 밤 위로 피어나는 부드러운 장미색과 라일락.", "濃いプラムの夜に咲く柔らかなローズとライラック。", "柔和玫瑰色与丁香色绽放于浓郁梅紫夜色。", "Rosa suave y lila floreciendo sobre una noche ciruela.", "Hồng dịu và tử đinh hương nở trên nền đêm mận đậm.", "Mawar lembut dan lilac bermekaran di malam plum yang kaya.", "", "يزهر الورد الناعم والأرجواني خلال ليلة غنية بالبرقوق."),
        "Pure black, polished silver, and a restrained icy glow." to row("Purong itim, makintab na pilak, at banayad na malamig na liwanag.", "순수한 검정, 매끈한 은빛, 절제된 얼음빛.", "純黒、磨かれた銀、控えめな氷の輝き。", "纯黑、亮银与克制的冰冷光芒。", "Negro puro, plata pulida y un brillo helado sutil.", "Đen thuần, bạc bóng và ánh băng tiết chế.", "Hitam murni, perak mengilap, dan cahaya es yang terkendali.", "", "أسود نقي، فضي مصقول، وتوهج جليدي مقيد."),
        "Injection" to row("Injection", "인젝션", "インジェクション", "注入", "Inyección", "Tiêm", "Injeksi", "", "الحقن"),
        "Patch" to row("Patch", "패치", "パッチ", "补丁", "Parche", "Bản vá", "Patch", "", "التصحيح"),
        "Identity shell" to row("Identity shell", "아이덴티티 셸", "ID シェル", "身份壳", "Shell de identidad", "Shell định danh", "Shell identitas", "", "قذيفة الهوية"),
        "INJECTION" to row("INJECTION", "인젝션", "インジェクション", "注入", "INYECCIÓN", "TIÊM", "INJEKSI", "", "الحقن"),
        "PATCH" to row("PATCH", "패치", "パッチ", "补丁", "PARCHE", "BẢN VÁ", "PATCH", "", "التصحيح"),
        "SHELL" to row("SHELL", "셸", "シェル", "壳", "SHELL", "SHELL", "SHELL", "", "شل"),
        "ROOT SETUP" to row("ROOT SETUP", "ROOT 설정", "ROOT 設定", "ROOT 设置", "CONFIGURACIÓN ROOT", "THIẾT LẬP ROOT", "PENYIAPAN ROOT", "", "إعداد الجذر"),
        "NON-ROOT SETUP" to row("NON-ROOT SETUP", "NON-ROOT 설정", "NON-ROOT 設定", "非 ROOT 设置", "CONFIGURACIÓN NON-ROOT", "THIẾT LẬP NON-ROOT", "PENYIAPAN NON-ROOT", "", "الإعداد غير الجذر"),
        "Root method" to row("Root method", "Root 방식", "Root 方式", "Root 方式", "Método Root", "Phương thức Root", "Metode Root", "", "طريقة الجذر"),
        "Non-root method" to row("Non-root method", "Non-root 방식", "Non-root 方式", "非 Root 方式", "Método Non-root", "Phương thức Non-root", "Metode Non-root", "", "طريقة غير الجذر"),
        "Selected method" to row("Napiling method", "선택한 방식", "選択した方法", "所选方式", "Método seleccionado", "Phương thức đã chọn", "Metode terpilih", "", "الطريقة المختارة"),
        "How root injection works" to row("Paano gumagana ang root injection", "Root 인젝션 작동 방식", "Root インジェクションの仕組み", "Root 注入的工作方式", "Cómo funciona la inyección root", "Cách tiêm root hoạt động", "Cara kerja injeksi root", "", "كيف يعمل حقن الجذور"),
        "How non-root injection works" to row("Paano gumagana ang non-root injection", "Non-root 인젝션 작동 방식", "Non-root インジェクションの仕組み", "非 Root 注入的工作方式", "Cómo funciona la inyección non-root", "Cách tiêm non-root hoạt động", "Cara kerja injeksi non-root", "", "كيف يعمل الحقن غير الجذري"),
        "How patched install works" to row("Paano gumagana ang patched install", "패치 설치 작동 방식", "パッチインストールの仕組み", "修补安装的工作方式", "Cómo funciona la instalación parcheada", "Cách cài đặt bản vá hoạt động", "Cara kerja instalasi patch", "", "كيف يعمل التثبيت المصحح"),
        "How exact-package shell works" to row("Paano gumagana ang exact-package shell", "정확한 패키지 셸 작동 방식", "完全パッケージシェルの仕組み", "精确包名壳的工作方式", "Cómo funciona el shell de paquete exacto", "Cách shell đúng tên gói hoạt động", "Cara kerja shell paket persis", "", "كيف تعمل قذيفة الحزمة بالضبط"),
        "Original game needed" to row("Kailangan ang original game", "원본 게임 필요", "元のゲームが必要", "需要原版游戏", "Se necesita el juego original", "Cần trò chơi gốc", "Game asli diperlukan", "", "مطلوب لعبة اصلية"),
        "Version and architecture not supported" to row("Hindi supported ang version at architecture", "버전과 아키텍처가 지원되지 않음", "バージョンとアーキテクチャは非対応", "不支持版本和架构", "Versión y arquitectura no compatibles", "Phiên bản và kiến trúc không được hỗ trợ", "Versi dan arsitektur tidak didukung", "", "الإصدار والهندسة المعمارية غير مدعومين"),
        "Game version or build not supported" to row("Hindi supported ang game version o build", "게임 버전 또는 빌드가 지원되지 않음", "ゲームのバージョンまたはビルドは非対応", "不支持游戏版本或构建号", "Versión o compilación no compatible", "Phiên bản hoặc bản dựng không được hỗ trợ", "Versi atau build game tidak didukung", "", "إصدار اللعبة أو الإصدار غير مدعوم"),
        "Architecture not supported" to row("Hindi supported ang architecture", "아키텍처가 지원되지 않음", "アーキテクチャは非対応", "不支持架构", "Arquitectura no compatible", "Kiến trúc không được hỗ trợ", "Arsitektur tidak didukung", "", "البنية غير مدعومة"),
        "Add-on needs repair" to row("Kailangang ayusin ang add-on", "애드온 복구 필요", "アドオンの修復が必要", "附加组件需要修复", "El complemento necesita reparación", "Tiện ích cần sửa chữa", "Add-on perlu diperbaiki", "", "الوظيفة الإضافية تحتاج إلى إصلاح"),
        "Patched install required" to row("Kailangan ang patched install", "패치 설치 필요", "パッチインストールが必要", "需要修补安装", "Se requiere instalación parcheada", "Cần cài đặt bản vá", "Instalasi patch diperlukan", "", "مطلوب تثبيت مصحح"),
        "Patched game update required" to row("Kailangan ang update ng patched game", "패치된 게임 업데이트 필요", "パッチ済みゲームの更新が必要", "需要更新已修补游戏", "Se requiere actualizar el juego parcheado", "Cần cập nhật trò chơi đã vá", "Pembaruan game patch diperlukan", "", "مطلوب تحديث اللعبة المصححة"),
        "Official game restore required" to row("Kailangang ibalik ang official game", "공식 게임 복원 필요", "公式ゲームの復元が必要", "需要恢复官方游戏", "Se requiere restaurar el juego oficial", "Cần khôi phục trò chơi chính thức", "Pemulihan game resmi diperlukan", "", "مطلوب استعادة اللعبة الرسمية"),
        "Exact-package shell required" to row("Kailangan ang exact-package shell", "정확한 패키지 셸 필요", "完全パッケージシェルが必要", "需要精确包名壳", "Se requiere shell de paquete exacto", "Cần shell đúng tên gói", "Shell paket persis diperlukan", "", "مطلوب غلاف الحزمة بالضبط"),
        "Add-on update in progress" to row("Kasalukuyang ina-update ang add-on", "애드온 업데이트 진행 중", "アドオン更新中", "附加组件正在更新", "Actualización del complemento en curso", "Đang cập nhật tiện ích", "Pembaruan add-on berlangsung", "", "تحديث الوظيفة الإضافية قيد التقدم"),
        "Running and ready" to row("Gumagana at handa", "실행 중이며 준비됨", "実行中・準備完了", "正在运行并已就绪", "En ejecución y listo", "Đang chạy và sẵn sàng", "Berjalan dan siap", "", "جارية وجاهزة"),
        "Ready to play" to row("Handang maglaro", "플레이 준비 완료", "プレイ準備完了", "可以开始游戏", "Listo para jugar", "Sẵn sàng chơi", "Siap bermain", "", "جاهز للعب"),
        "Installed game" to row("Installed game", "설치된 게임", "インストール済みゲーム", "已安装游戏", "Juego instalado", "Trò chơi đã cài", "Game terinstal", "", "لعبة مثبتة"),
        "Not installed" to row("Hindi installed", "설치되지 않음", "未インストール", "未安装", "No instalado", "Chưa cài đặt", "Belum terinstal", "", "غير مثبت"),
        "Supported versions" to row("Supported versions", "지원 버전", "対応バージョン", "支持的版本", "Versiones compatibles", "Phiên bản được hỗ trợ", "Versi yang didukung", "", "الإصدارات المدعومة"),
        "Supported builds" to row("Supported builds", "지원 빌드", "対応ビルド", "支持的构建号", "Compilaciones compatibles", "Bản dựng được hỗ trợ", "Build yang didukung", "", "البنيات المدعومة"),
        "Supported architecture" to row("Supported architecture", "지원 아키텍처", "対応アーキテクチャ", "支持的架构", "Arquitectura compatible", "Kiến trúc được hỗ trợ", "Arsitektur yang didukung", "", "الهندسة المعمارية المدعومة"),
        "Declared by add-on" to row("Idineklara ng add-on", "애드온에 선언됨", "アドオンで宣言", "由附加组件声明", "Declarado por el complemento", "Do tiện ích khai báo", "Dideklarasikan add-on", "", "أعلن عن طريق الوظيفة الإضافية"),
        "Check temporarily unavailable" to row("Pansamantalang hindi available ang check", "확인을 일시적으로 사용할 수 없음", "確認を一時的に利用できません", "暂时无法检查", "Comprobación temporalmente no disponible", "Tạm thời không thể kiểm tra", "Pemeriksaan sementara tidak tersedia", "", "الشيك غير متاح مؤقتا"),
        "TEST" to row("TEST", "테스트", "テスト", "测试", "PRUEBA", "THỬ NGHIỆM", "UJI", "", "اختبار"),
        "Support code copied" to row("Nakopya ang support code", "지원 코드가 복사됨", "サポートコードをコピーしました", "支持代码已复制", "Código de soporte copiado", "Đã sao chép mã hỗ trợ", "Kode dukungan disalin", "", "تم نسخ رمز الدعم"),
        "Support code is unavailable" to row("Hindi available ang support code", "지원 코드를 사용할 수 없음", "サポートコードを利用できません", "支持代码不可用", "El código de soporte no está disponible", "Mã hỗ trợ không khả dụng", "Kode dukungan tidak tersedia", "", "رمز الدعم غير متوفر"),
        "Finish the current shell removal first" to row("Tapusin muna ang kasalukuyang pag-alis ng shell", "현재 셸 제거를 먼저 완료하세요", "現在のシェル削除を先に完了してください", "请先完成当前壳的移除", "Termina primero la eliminación del shell actual", "Hãy hoàn tất việc gỡ shell hiện tại trước", "Selesaikan penghapusan shell saat ini terlebih dahulu", "", "قم بإنهاء عملية إزالة الصدفة الحالية أولاً"),
        "Diagnostics copied" to row("Nakopya ang diagnostics", "진단 정보가 복사됨", "診断情報をコピーしました", "诊断信息已复制", "Diagnósticos copiados", "Đã sao chép chẩn đoán", "Diagnostik disalin", "", "تم نسخ التشخيص"),
        "Device security and game kernels behave differently. Start with the recommended route, then use the alternative if setup is blocked or the game closes during startup." to row("Magkakaiba ang device security at game kernels. Magsimula sa recommended route, saka gamitin ang alternative kung ma-block ang setup o magsara ang laro.", "기기 보안과 게임 커널은 서로 다릅니다. 권장 방식부터 시작하고 설정이 차단되거나 게임이 종료되면 다른 방식을 사용하세요.", "デバイスのセキュリティとゲームカーネルは異なります。推奨方法から始め、設定がブロックされるかゲームが終了する場合は別の方法を使ってください。", "设备安全机制和游戏内核各不相同。请先使用推荐方式；如果设置受阻或游戏启动时关闭，请改用另一种方式。", "La seguridad del dispositivo y los núcleos del juego varían. Empieza con la opción recomendada y usa la alternativa si se bloquea la configuración o se cierra el juego.", "Bảo mật thiết bị và nhân trò chơi hoạt động khác nhau. Hãy bắt đầu với cách được đề xuất, rồi dùng cách khác nếu thiết lập bị chặn hoặc trò chơi đóng khi khởi động.", "Keamanan perangkat dan kernel game berbeda. Mulai dengan cara yang direkomendasikan, lalu gunakan alternatif jika penyiapan diblokir atau game tertutup saat mulai.", "", "يتصرف أمان الجهاز ونواة اللعبة بشكل مختلف. ابدأ بالمسار الموصى به، ثم استخدم البديل إذا تم حظر الإعداد أو إغلاق اللعبة أثناء بدء التشغيل."),
        "Use this code when you need to connect this phone to a pass or request help. It does not reveal your private access key and cannot unlock another phone." to row("Gamitin ang code para i-connect ang phone sa pass o humingi ng tulong. Hindi nito ipinapakita ang private access key at hindi nito maa-unlock ang ibang phone.", "이 휴대전화를 패스에 연결하거나 도움을 요청할 때 이 코드를 사용하세요. 개인 접근 키는 노출되지 않으며 다른 휴대전화의 잠금을 해제할 수 없습니다.", "この端末をパスに接続したりサポートを依頼したりする際に使います。秘密のアクセスキーは公開されず、別の端末を解除することもできません。", "需要将此手机连接到通行证或请求帮助时使用此代码。它不会泄露私人访问密钥，也无法解锁其他手机。", "Usa este código para vincular el teléfono a un pase o pedir ayuda. No revela tu clave privada ni puede desbloquear otro teléfono.", "Dùng mã này để liên kết điện thoại với thẻ hoặc yêu cầu trợ giúp. Mã không tiết lộ khóa truy cập riêng và không thể mở khóa điện thoại khác.", "Gunakan kode ini untuk menghubungkan ponsel ke pass atau meminta bantuan. Kode tidak mengungkap kunci akses pribadi dan tidak dapat membuka ponsel lain.", "", "استخدم هذا الرمز عندما تحتاج إلى توصيل هذا الهاتف ببطاقة مرور أو طلب المساعدة. ولا يكشف عن مفتاح الوصول الخاص بك ولا يمكنه فتح هاتف آخر."),
        "Adds access to this phone" to row("Nagdadagdag ng access sa phone na ito", "이 휴대전화에 접근 권한 추가", "この端末にアクセスを追加", "为此手机添加访问权限", "Añade acceso a este teléfono", "Thêm quyền truy cập cho điện thoại này", "Menambahkan akses ke ponsel ini", "", "يضيف الوصول إلى هذا الهاتف"),
        "This lets a pass be linked to the device you are holding." to row("Pinapayagan nitong mai-link ang pass sa device na hawak mo.", "패스를 현재 기기에 연결할 수 있습니다.", "パスを現在のデバイスに関連付けられます。", "这样可将通行证关联到您手中的设备。", "Permite vincular un pase al dispositivo que tienes.", "Cho phép liên kết thẻ với thiết bị bạn đang cầm.", "Memungkinkan pass ditautkan ke perangkat yang Anda pegang.", "", "يتيح ذلك ربط البطاقة بالجهاز الذي تحمله."),
        "Works across launcher modes" to row("Gumagana sa lahat ng launcher mode", "런처 모드 간에 작동", "ランチャーモード間で共通", "适用于不同启动器模式", "Funciona en todos los modos", "Hoạt động trên mọi chế độ", "Berfungsi di semua mode peluncur", "", "يعمل عبر أوضاع المشغل"),
        "Root and Non-root use the same device identity, so you do not need two separate passes." to row("Iisa ang device identity ng Root at Non-root kaya hindi kailangan ng dalawang pass.", "Root와 Non-root는 같은 기기 ID를 사용하므로 별도 패스가 필요하지 않습니다.", "Root と Non-root は同じデバイス ID を使うため、別々のパスは不要です。", "Root 和非 Root 使用相同的设备身份，因此无需两个独立通行证。", "Root y Non-root usan la misma identidad, así que no necesitas dos pases.", "Root và Non-root dùng cùng danh tính thiết bị nên không cần hai thẻ riêng.", "Root dan Non-root memakai identitas perangkat yang sama, jadi tidak perlu dua pass.", "", "يستخدم الجذر وغير الجذر نفس هوية الجهاز، لذلك لا تحتاج إلى مرورين منفصلين."),
        "Safe to share when needed" to row("Ligtas ibahagi kapag kailangan", "필요할 때 안전하게 공유", "必要なとき安全に共有", "需要时可安全分享", "Seguro para compartir cuando haga falta", "An toàn để chia sẻ khi cần", "Aman dibagikan saat diperlukan", "", "آمنة للمشاركة عند الحاجة"),
        "It is a device lookup code, not your private access key." to row("Device lookup code ito, hindi ang private access key mo.", "기기 조회 코드이며 개인 접근 키가 아닙니다.", "端末検索コードであり、秘密のアクセスキーではありません。", "这是设备查询代码，不是您的私人访问密钥。", "Es un código de búsqueda del dispositivo, no tu clave privada.", "Đây là mã tra cứu thiết bị, không phải khóa truy cập riêng.", "Ini kode pencarian perangkat, bukan kunci akses pribadi Anda.", "", "إنه رمز للبحث عن الجهاز، وليس مفتاح الوصول الخاص بك.")
    )

    private const val LANGUAGE_CONFIRMED = "language_confirmed"
}

@Composable
internal fun LocalizedText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    LauncherLocalization.initialize(LocalContext.current)
    MaterialText(
        text = LauncherLocalization.translate(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
