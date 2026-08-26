package com.android.support;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/** Offline, deterministic localization for menu chrome, descriptors, and native notices. */
public final class OfflineTranslator {
    public static final String PREF_LANGUAGE = "menu_language";
    private static final String PREF_LANGUAGE_SCHEMA = "menu_language_schema";
    private static final int LANGUAGE_SCHEMA = 2;
    public static final int ENGLISH = 0;

    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static int preferredLanguage = ENGLISH;

    private static final String[] COMMON_ENGLISH = {
            "SETTINGS", "Open menu settings", "Preferences", "Navigation", "Language",
            "English", "Filipino", "Korean", "Japanese", "Chinese (Simplified)", "Spanish",
            "Vietnamese", "Indonesian", "Return to features", "Save feature preferences",
            "Expanded panel height", "Menu animations", "Color animations", "FOR TESTING",
            "Hide  |  hold to stop", "Minimize", "Icon hidden. Remember the hidden icon position",
            "Menu killed", "Force load menu",
            "Save preferences was been enabled. Waiting for game lib to be loaded...\n\nForce load menu may not apply mods instantly. You would need to reactivate them again",
            "Failed to launch the mod menu"
    };

    static {
        add("fil", new String[]{
                "MGA SETTING", "Buksan ang mga setting ng menu", "Mga Kagustuhan", "Nabigasyon", "Wika",
                "Ingles", "Filipino", "Koreano", "Hapones", "Tsino (Pinasimple)", "Espanyol",
                "Vietnamese", "Indonesian", "Bumalik sa mga feature", "I-save ang mga kagustuhan ng feature",
                "Taas ng pinalawak na panel", "Mga animation ng menu", "Mga animation ng kulay", "PARA SA PAGSUBOK",
                "Itago  |  pindutin nang matagal upang ihinto", "I-minimize", "Nakatago ang icon. Tandaan ang posisyon nito",
                "Itinigil ang menu", "Pilitang i-load ang menu",
                "Naka-enable ang pag-save ng mga kagustuhan. Hinihintay na ma-load ang game library...\n\nMaaaring hindi agad mailapat ng pilitang pag-load ang mga mod. I-activate muli ang mga ito.",
                "Hindi mailunsad ang mod menu"
        });
        add("ko", new String[]{
                "설정", "메뉴 설정 열기", "환경설정", "탐색", "언어",
                "영어", "필리핀어", "한국어", "일본어", "중국어(간체)", "스페인어",
                "베트남어", "인도네시아어", "기능으로 돌아가기", "기능 설정 저장",
                "확장 패널 높이", "메뉴 애니메이션", "색상 애니메이션", "테스트용",
                "숨기기  |  길게 눌러 종료", "최소화", "아이콘이 숨겨졌습니다. 숨긴 위치를 기억하세요",
                "메뉴가 종료되었습니다", "메뉴 강제 로드", "설정 저장이 활성화되었습니다. 게임 라이브러리를 기다리는 중...\n\n강제 로드는 모드를 즉시 적용하지 못할 수 있습니다. 다시 활성화하세요.",
                "모드 메뉴를 실행하지 못했습니다"
        });
        add("ja", new String[]{
                "設定", "メニュー設定を開く", "環境設定", "ナビゲーション", "言語",
                "英語", "フィリピン語", "韓国語", "日本語", "中国語（簡体字）", "スペイン語",
                "ベトナム語", "インドネシア語", "機能に戻る", "機能設定を保存",
                "展開パネルの高さ", "メニューアニメーション", "カラーアニメーション", "テスト用",
                "隠す  |  長押しで停止", "最小化", "アイコンを隠しました。隠した位置を覚えてください",
                "メニューを停止しました", "メニューを強制読み込み", "設定保存が有効です。ゲームライブラリの読み込みを待っています...\n\n強制読み込みではMODがすぐ適用されない場合があります。再度有効にしてください。",
                "MODメニューを起動できませんでした"
        });
        add("zh", new String[]{
                "设置", "打开菜单设置", "偏好设置", "导航", "语言",
                "英语", "菲律宾语", "韩语", "日语", "简体中文", "西班牙语",
                "越南语", "印度尼西亚语", "返回功能", "保存功能偏好",
                "展开面板高度", "菜单动画", "颜色动画", "仅供测试",
                "隐藏  |  长按停止", "最小化", "图标已隐藏。请记住隐藏位置",
                "菜单已停止", "强制加载菜单", "已启用偏好保存。正在等待游戏库加载...\n\n强制加载可能不会立即应用模组，需要重新启用。",
                "无法启动模组菜单"
        });
        add("es", new String[]{
                "AJUSTES", "Abrir ajustes del menú", "Preferencias", "Navegación", "Idioma",
                "Inglés", "Filipino", "Coreano", "Japonés", "Chino (simplificado)", "Español",
                "Vietnamita", "Indonesio", "Volver a las funciones", "Guardar preferencias de funciones",
                "Altura del panel expandido", "Animaciones del menú", "Animaciones de color", "PARA PRUEBAS",
                "Ocultar  |  mantén pulsado para detener", "Minimizar", "Icono oculto. Recuerda su posición",
                "Menú detenido", "Forzar carga del menú", "El guardado de preferencias está activado. Esperando la biblioteca del juego...\n\nLa carga forzada puede no aplicar los mods al instante. Vuelve a activarlos.",
                "No se pudo iniciar el menú mod"
        });
        add("vi", new String[]{
                "CÀI ĐẶT", "Mở cài đặt menu", "Tùy chọn", "Điều hướng", "Ngôn ngữ",
                "Tiếng Anh", "Tiếng Filipino", "Tiếng Hàn", "Tiếng Nhật", "Tiếng Trung (Giản thể)", "Tiếng Tây Ban Nha",
                "Tiếng Việt", "Tiếng Indonesia", "Quay lại tính năng", "Lưu tùy chọn tính năng",
                "Chiều cao bảng mở rộng", "Hoạt ảnh menu", "Hoạt ảnh màu", "DÙNG ĐỂ THỬ NGHIỆM",
                "Ẩn  |  giữ để dừng", "Thu nhỏ", "Biểu tượng đã ẩn. Hãy nhớ vị trí",
                "Menu đã dừng", "Buộc tải menu", "Đã bật lưu tùy chọn. Đang chờ thư viện trò chơi...\n\nBuộc tải có thể chưa áp dụng mod ngay. Hãy bật lại chúng.",
                "Không thể khởi chạy menu mod"
        });
        add("id", new String[]{
                "PENGATURAN", "Buka pengaturan menu", "Preferensi", "Navigasi", "Bahasa",
                "Inggris", "Filipina", "Korea", "Jepang", "Tionghoa (Sederhana)", "Spanyol",
                "Vietnam", "Indonesia", "Kembali ke fitur", "Simpan preferensi fitur",
                "Tinggi panel diperluas", "Animasi menu", "Animasi warna", "UNTUK PENGUJIAN",
                "Sembunyikan  |  tahan untuk berhenti", "Minimalkan", "Ikon disembunyikan. Ingat posisinya",
                "Menu dihentikan", "Paksa muat menu", "Penyimpanan preferensi aktif. Menunggu pustaka game dimuat...\n\nPemuatan paksa mungkin tidak langsung menerapkan mod. Aktifkan kembali.",
                "Gagal menjalankan menu mod"
        });

        addLocalized("Item Receive Multipliers",
                "Mga Multiplier ng Natatanggap na Item",
                "아이템 획득 배수",
                "アイテム獲得倍率",
                "物品获取倍数",
                "Multiplicadores de objetos recibidos",
                "Hệ số vật phẩm nhận được",
                "Pengali Perolehan Item");
        addLocalized("Seeds Multiplier (0-1 = normal)",
                "Multiplier ng Buto (0-1 = normal)",
                "씨앗 배수 (0-1 = 기본)",
                "種の倍率 (0-1 = 通常)",
                "种子倍数（0-1 = 正常）",
                "Multiplicador de semillas (0-1 = normal)",
                "Hệ số hạt giống (0-1 = bình thường)",
                "Pengali Benih (0-1 = normal)");
        addLocalized("Materials & Event Items Multiplier (0-1 = normal)",
                "Multiplier ng Materyales at Event Item (0-1 = normal)",
                "재료 및 이벤트 아이템 배수 (0-1 = 기본)",
                "素材・イベントアイテム倍率 (0-1 = 通常)",
                "材料和活动物品倍数（0-1 = 正常）",
                "Multiplicador de materiales y objetos de evento (0-1 = normal)",
                "Hệ số nguyên liệu và vật phẩm sự kiện (0-1 = bình thường)",
                "Pengali Material & Item Event (0-1 = normal)");
        addLocalized("Token Tickets Multiplier (0-1 = normal)",
                "Multiplier ng Token Ticket (0-1 = normal)",
                "토큰 티켓 배수 (0-1 = 기본)",
                "トークンチケット倍率 (0-1 = 通常)",
                "代币券倍数（0-1 = 正常）",
                "Multiplicador de boletos de ficha (0-1 = normal)",
                "Hệ số vé token (0-1 = bình thường)",
                "Pengali Tiket Token (0-1 = normal)");
        addLocalized("Special Event Tickets Multiplier (0-1 = normal)",
                "Multiplier ng Espesyal na Event Ticket (0-1 = normal)",
                "특별 이벤트 티켓 배수 (0-1 = 기본)",
                "特別イベントチケット倍率 (0-1 = 通常)",
                "特殊活动券倍数（0-1 = 正常）",
                "Multiplicador de boletos de evento especial (0-1 = normal)",
                "Hệ số vé sự kiện đặc biệt (0-1 = bình thường)",
                "Pengali Tiket Event Khusus (0-1 = normal)");
        addLocalized("In-run Coins & Mode Currency Multiplier (0-1 = normal)",
                "Multiplier ng Coin sa Run at Mode Currency (0-1 = normal)",
                "런 중 코인 및 모드 재화 배수 (0-1 = 기본)",
                "ラン中コイン・モード通貨倍率 (0-1 = 通常)",
                "局内金币和模式货币倍数（0-1 = 正常）",
                "Multiplicador de monedas de partida y divisas de modo (0-1 = normal)",
                "Hệ số xu trong lượt và tiền tệ chế độ (0-1 = bình thường)",
                "Pengali Koin Dalam Run & Mata Uang Mode (0-1 = normal)");
        addLocalized("Weapon Projectile & Effect Size Multiplier (1x-10x)",
                "Multiplier ng Laki ng Projectile at Effect ng Sandata (1x-10x)",
                "무기 투사체 및 효과 크기 배수 (1x-10x)",
                "武器の投射物・エフェクトサイズ倍率 (1x-10x)",
                "武器投射物与特效大小倍数（1x-10x）",
                "Multiplicador de tamaño de proyectiles y efectos de armas (1x-10x)",
                "Hệ số kích thước đạn và hiệu ứng vũ khí (1x-10x)",
                "Pengali Ukuran Proyektil & Efek Senjata (1x-10x)");
        addLocalized("Attack Speed Multiplier (1x-10x)",
                "Multiplier ng Bilis ng Pag-atake (1x-10x)",
                "공격 속도 배수 (1x-10x)",
                "攻撃速度倍率 (1x-10x)",
                "攻击速度倍数（1x-10x）",
                "Multiplicador de velocidad de ataque (1x-10x)",
                "Hệ số tốc độ tấn công (1x-10x)",
                "Pengali Kecepatan Serangan (1x-10x)");
        addLocalized("Max Level All Bodyguards",
                "I-max Level ang Lahat ng Bodyguard",
                "\uBAA8\uB4E0 \uD638\uC704\uBCD1 \uCD5C\uB300 \uB808\uBCA8",
                "\u3059\u3079\u3066\u306E\u8B77\u885B\u3092\u6700\u5927\u30EC\u30D9\u30EB\u306B\u3059\u308B",
                "\u6240\u6709\u62A4\u536B\u6EE1\u7EA7",
                "Subir al nivel m\u00E1ximo a todos los guardaespaldas",
                "\u0110\u01B0a t\u1EA5t c\u1EA3 v\u1EC7 s\u0129 l\u00EAn c\u1EA5p t\u1ED1i \u0111a",
                "Maksimalkan Level Semua Pengawal");
        addLocalized("Free Everything",
                "Libre ang Lahat",
                "\uBAA8\uB450 \uBB34\uB8CC",
                "\u3059\u3079\u3066\u7121\u6599",
                "\u5168\u90E8\u514D\u8D39",
                "Todo gratis",
                "Mi\u1EC5n ph\u00ED t\u1EA5t c\u1EA3",
                "Semua Gratis");
        addLocalized("Bodyguard max-level enabled. Open or refresh the Bodyguard page once.",
                "Naka-enable ang max level ng bodyguard. Buksan o i-refresh ang Bodyguard page nang isang beses.",
                "\uD638\uC704\uBCD1 \uCD5C\uB300 \uB808\uBCA8\uC774 \uD65C\uC131\uD654\uB418\uC5C8\uC2B5\uB2C8\uB2E4. \uD638\uC704\uBCD1 \uD398\uC774\uC9C0\uB97C \uD55C \uBC88 \uC5F4\uAC70\uB098 \uC0C8\uB85C \uACE0\uCE68\uD558\uC138\uC694.",
                "\u8B77\u885B\u306E\u6700\u5927\u30EC\u30D9\u30EB\u5316\u3092\u6709\u52B9\u306B\u3057\u307E\u3057\u305F\u3002\u8B77\u885B\u30DA\u30FC\u30B8\u30921\u5EA6\u958B\u304F\u304B\u66F4\u65B0\u3057\u3066\u304F\u3060\u3055\u3044\u3002",
                "\u62A4\u536B\u6EE1\u7EA7\u5DF2\u542F\u7528\u3002\u8BF7\u6253\u5F00\u6216\u5237\u65B0\u4E00\u6B21\u62A4\u536B\u9875\u9762\u3002",
                "Nivel m\u00E1ximo de guardaespaldas activado. Abre o actualiza una vez la p\u00E1gina de guardaespaldas.",
                "\u0110\u00E3 b\u1EADt c\u1EA5p t\u1ED1i \u0111a cho v\u1EC7 s\u0129. H\u00E3y m\u1EDF ho\u1EB7c l\u00E0m m\u1EDBi trang V\u1EC7 s\u0129 m\u1ED9t l\u1EA7n.",
                "Level maksimum pengawal diaktifkan. Buka atau segarkan halaman Pengawal sekali.");
    }

    private OfflineTranslator() {
    }

    public static void initialize(Context context) {
        if (context == null) return;
        Preferences preferences = Preferences.with(context);
        int savedLanguage = preferences.readInt(PREF_LANGUAGE, ENGLISH);
        if (preferences.readInt(PREF_LANGUAGE_SCHEMA, 0) < LANGUAGE_SCHEMA) {
            savedLanguage = savedLanguage <= 1 ? ENGLISH : savedLanguage - 1;
            preferences.writeInt(PREF_LANGUAGE, savedLanguage);
            preferences.writeInt(PREF_LANGUAGE_SCHEMA, LANGUAGE_SCHEMA);
        }
        preferredLanguage = clampLanguage(savedLanguage);
    }

    public static int getPreferredLanguage() {
        return preferredLanguage;
    }

    public static void setPreferredLanguage(Context context, int language) {
        preferredLanguage = clampLanguage(language);
        if (context == null) return;
        Preferences preferences = Preferences.with(context);
        preferences.writeInt(PREF_LANGUAGE, preferredLanguage);
        preferences.writeInt(PREF_LANGUAGE_SCHEMA, LANGUAGE_SCHEMA);
    }

    public static String tr(String english) {
        if (english == null || english.length() == 0 || preferredLanguage == ENGLISH) return english;
        Map<String, String> language = TRANSLATIONS.get(languageCode());
        String translated = language == null ? null : language.get(english);
        return translated == null || translated.length() == 0 ? english : translated;
    }

    public static String translateForNative(String english) {
        return tr(english);
    }

    /** Translates visible descriptor fields without changing parser tokens or numeric IDs. */
    public static String translateFeatureDescriptor(String descriptor) {
        if (descriptor == null || descriptor.length() == 0) return descriptor;
        boolean testing = descriptor.endsWith("_ForTesting");
        String source = testing
                ? descriptor.substring(0, descriptor.length() - "_ForTesting".length())
                : descriptor;
        StringBuilder prefix = new StringBuilder();

        int underscore = source.indexOf('_');
        if (underscore > 0 && source.substring(0, underscore).matches("-?[0-9]+")) {
            prefix.append(source, 0, underscore + 1);
            source = source.substring(underscore + 1);
        }
        if (source.startsWith("CollapseAdd_")) {
            prefix.append("CollapseAdd_");
            source = source.substring("CollapseAdd_".length());
        }

        String[] parts = source.split("_", -1);
        if (parts.length > 1) {
            String type = parts[0];
            if ("Spinner".equals(type) || "MultiSelectSpinner".equals(type)) {
                parts[1] = tr(parts[1]);
                if (parts.length > 2) parts[2] = translateCsv(parts[2]);
            } else if ("InputValue".equals(type) || "InputFloat".equals(type)
                    || "InputLValue".equals(type)) {
                parts[parts.length - 1] = tr(parts[parts.length - 1]);
            } else if (!"GroupEnd".equals(type) && !"CollapseEnd".equals(type)) {
                parts[1] = tr(parts[1]);
            }
        }

        StringBuilder output = new StringBuilder(prefix);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) output.append('_');
            output.append(parts[i]);
        }
        if (testing) output.append("_ForTesting");
        return output.toString();
    }

    private static String translateCsv(String csv) {
        String[] values = csv.split(",", -1);
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) output.append(',');
            output.append(tr(values[i]));
        }
        return output.toString();
    }

    private static void add(String code, String[] translations) {
        if (translations.length != COMMON_ENGLISH.length) {
            throw new IllegalStateException("Translation table mismatch for " + code);
        }
        Map<String, String> language = new HashMap<>();
        for (int i = 0; i < COMMON_ENGLISH.length; i++) {
            language.put(COMMON_ENGLISH[i], translations[i]);
        }
        language.put("<font color='#E8B86A'>Return to features</font>",
                "<font color='#E8B86A'>" + translations[13] + "</font>");
        TRANSLATIONS.put(code, language);
    }

    private static void addLocalized(String english, String filipino, String korean,
                                     String japanese, String chinese, String spanish,
                                     String vietnamese, String indonesian) {
        TRANSLATIONS.get("fil").put(english, filipino);
        TRANSLATIONS.get("ko").put(english, korean);
        TRANSLATIONS.get("ja").put(english, japanese);
        TRANSLATIONS.get("zh").put(english, chinese);
        TRANSLATIONS.get("es").put(english, spanish);
        TRANSLATIONS.get("vi").put(english, vietnamese);
        TRANSLATIONS.get("id").put(english, indonesian);
    }

    private static int clampLanguage(int language) {
        return Math.max(ENGLISH, Math.min(7, language));
    }

    private static String languageCode() {
        switch (preferredLanguage) {
            case 1: return "fil";
            case 2: return "ko";
            case 3: return "ja";
            case 4: return "zh";
            case 5: return "es";
            case 6: return "vi";
            case 7: return "id";
            default: return "en";
        }
    }
}
