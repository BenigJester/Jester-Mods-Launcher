package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

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

        add("pt", new String[]{
                "CONFIGURAÇÕES", "Abrir configurações do menu", "Preferências", "Navegação", "Idioma",
                "Inglês", "Filipino", "Coreano", "Japonês", "Chinês (Simplificado)", "Espanhol",
                "Vietnamita", "Indonésio", "Voltar aos recursos", "Salvar preferências dos recursos",
                "Altura do painel expandido", "Animações do menu", "Animações de cores", "PARA TESTES",
                "Ocultar  |  segure para parar", "Minimizar", "Ícone oculto. Lembre-se da posição do ícone oculto",
                "Menu encerrado", "Forçar carregamento do menu",
                "O salvamento de preferências foi ativado. Aguardando o carregamento da biblioteca do jogo...\n\nO carregamento forçado pode não aplicar os mods imediatamente. Você precisará reativá-los.",
                "Falha ao iniciar o menu mod"
        });
        add("ar", new String[]{
                "الإعدادات",
                "فتح إعدادات القائمة",
                "التفضيلات",
                "الملاحة",
                "اللغة",
                "الإنجليزية",
                "فلبينية",
                "الكورية",
                "اليابانية",
                "الصينية (المبسطة)",
                "الاسبانية",
                "الفيتنامية",
                "الاندونيسية",
                "العودة إلى الميزات",
                "حفظ تفضيلات الميزة",
                "ارتفاع اللوحة الموسعة",
                "الرسوم المتحركة القائمة",
                "الرسوم المتحركة الملونة",
                "للاختبار",
                "إخفاء |  عقد للتوقف",
                "تصغير",
                "أيقونة مخفية. تذكر موضع الأيقونة المخفية",
                "قتل القائمة",
                "قائمة تحميل القوة",
                "تم تمكين حفظ التفضيلات. في انتظار تحميل اللعبة lib...\n\nقد لا تطبق قائمة التحميل القسري التعديلات على الفور. سوف تحتاج إلى إعادة تنشيطها مرة أخرى",
                "فشل في تشغيل قائمة التعديل"
        });

        // BEGIN GENERATED COMPLETE MODULE TRANSLATIONS
        addComplete("<font color='#E8B86A'>Return to features</font>",
                "<font color='#E8B86A'>Bumalik sa mga tampok</font>", "<font color='#E8B86A'>기능으로 돌아가기</font>", "<font color='#E8B86A'>機能へ戻る</font>", "<font color='#E8B86A'>返回功能</font>",
                "<font color='#E8B86A'>Volver a funciones</font>", "<font color='#E8B86A'>Quay lại các tính năng</font>", "<font color='#E8B86A'>Kembali ke fitur</font>", "<font color='#E8B86A'>Retornar aos recursos</font>");
        addComplete("All Options",
                "Lahat ng Opsyon", "모든 옵션", "すべてのオプション", "所有选项",
                "Todas las opciones", "Tất cả tùy chọn", "Semua Opsi", "Todas as Opções");
        addComplete("Alpha",
                "Alpha", "알파", "アルファ", "阿尔法",
                "Alfa", "Alpha", "Alpha", "Alfa");
        addComplete("Apply Grouped Values",
                "Ilapat ang Pinangkat na Halaga", "그룹 값 적용", "グループ値を適用", "应用分组数值",
                "Aplicar valores agrupados", "Áp dụng giá trị nhóm", "Terapkan Nilai yang Digabungkan", "Aplicar Valores Agrupados");
        addComplete("Attack Speed Multiplier (1x-10x)",
                "Multiplier ng Bilis ng Atake (1x-10x)", "공격 속도 배율 (1배-10배)", "攻撃速度倍率（1倍-10倍）", "攻击速度倍数 (1x-10x)",
                "Multiplicador de Velocidad de Ataque (1x-10x)", "Hệ Số Tốc Độ Tấn Công (1x-10x)", "Pengali Kecepatan Serangan (1x-10x)", "Multiplicador de Velocidade de Ataque (1x-10x)");
        addComplete("Automatic ID Example",
                "Halimbawa ng Awtomatikong ID", "자동 ID 예시", "自動ID例", "自动识别示例",
                "Ejemplo de ID Automático", "Ví Dụ Nhận Dạng Tự Động", "Contoh ID Otomatis", "Exemplo de ID Automático");
        addComplete("Back In Parent Collapse",
                "Bumalik sa Parent Collapse", "부모 접기에서 뒤로", "親の折りたたみに戻る", "返回父级折叠",
                "Volver a la Contracción del Padre", "Quay Lại Trong Cha Thu Gọn", "Kembali ke Penggabungan Induk", "Voltar no Colapso do Pai");
        addComplete("Basic Toggle",
                "Pangunahing Toggle", "기본 토글", "基本トグル", "基础切换",
                "Alternar Básico", "Bật/Tắt Cơ Bản", "Togol Dasar", "Alternância Básica");
        addComplete("Beta",
                "Beta", "베타", "ベータ", "测试版",
                "Beta", "Beta", "Beta", "Beta");
        addComplete("Bodyguard max-level enabled. Open or refresh the Bodyguard page once.",
                "Bodyguard max-level enabled. Buksan o i-refresh ang pahina ng Bodyguard minsan.", "보디가드 최고 레벨 활성화. 보디가드 페이지를 한 번 열거나 새로 고침하세요.", "ボディーガードの最大レベルが有効になりました。ボディーガードのページを一度開くか更新してください。", "保镖已启用最高等级。打开或刷新保镖页面一次。",
                "Guardia personal nivel máximo activado. Abra o actualice la página del Guardia personal una vez.", "Bảo vệ ở mức tối đa đã bật. Mở hoặc làm mới trang Bảo vệ một lần.", "Bodyguard level-maks diaktifkan. Buka atau segarkan halaman Bodyguard sekali.", "Bodyguard nível máximo ativado. Abra ou atualize a página do Bodyguard uma vez.");
        addComplete("Checked automatically when the game library is available.",
                "Awtomatikong sinusuri kapag available ang library ng laro.", "게임 라이브러리가 사용 가능할 때 자동으로 확인됩니다.", "ゲームライブラリが利用可能なときに自動的にチェックされます。", "游戏库可用时自动检查。",
                "Marcado automáticamente cuando la biblioteca de juegos está disponible.", "Được kiểm tra tự động khi thư viện trò chơi có sẵn.", "Dicentang secara otomatis saat perpustakaan game tersedia.", "Verificado automaticamente quando a biblioteca de jogos está disponível.");
        addComplete("Checking game compatibility...\n\nIf the game library loads late, the menu will continue automatically.",
                "Sinusuri ang pagkakatugma ng laro...\n\nKung mabagal mag-load ang library ng laro, magpapatuloy ang menu nang awtomatiko.", "게임 호환성 확인 중...\n\n게임 라이브러리가 늦게 로드되면 메뉴가 자동으로 계속 진행됩니다.", "ゲームの互換性をチェック中...\n\nゲームライブラリの読み込みが遅れる場合、メニューは自動的に続行されます。", "正在检查游戏兼容性...\n\n如果游戏库加载延迟，菜单将自动继续。",
                "Comprobando la compatibilidad del juego...\n\nSi la biblioteca de juegos se carga tarde, el menú continuará automáticamente.", "Đang kiểm tra khả năng tương thích của trò chơi...\n\nNếu thư viện trò chơi tải muộn, menu sẽ tiếp tục tự động.", "Memeriksa kompatibilitas game... \n\nJika perpustakaan game terlambat dimuat, menu akan berlanjut secara otomatis.", "Verificando compatibilidade do jogo...\n\nSe a biblioteca de jogos carregar tarde, o menu continuará automaticamente.");
        addComplete("Checking game library",
                "Sinusuri ang library ng laro", "게임 라이브러리 확인 중", "ゲームライブラリをチェック中", "正在检查游戏库",
                "Comprobando la biblioteca de juegos", "Đang kiểm tra thư viện trò chơi", "Memeriksa perpustakaan game", "Verificando biblioteca de jogos");
        addComplete("Collapse Child Toggle",
                "I-collapse ang Toggle ng Anak", "자식 토글 접기", "子トグルを折りたたむ", "折叠子选项",
                "Colapsar alternancia de hijos", "Thu gọn chuyển đổi con", "Sembunyikan Toggle Anak", "Recolher Alternativa de Filhos");
        addComplete("Connected group action pressed.",
                "Pinindot ang konektadong group action.", "연결된 그룹 액션이 눌렸습니다.", "接続されたグループアクションが押されました。", "已按下连接组操作。",
                "Acción de grupo conectada presionada.", "Hành động nhóm kết nối đã được nhấn.", "Tindakan kelompok yang terhubung ditekan.", "Ação de grupo conectada pressionada.");
        addComplete("CRASH! check:",
                "CRASH! suriin:", "충돌! 확인:", "クラッシュ！確認:", "崩溃！检查：",
                "¡CRASH! verificar:", "LỖI! kiểm tra:", "CRASH! periksa:", "FALHA! verifique:");
        addComplete("CRASH! Could not save log.",
                "CRASH! Hindi masave ang log.", "충돌! 로그를 저장할 수 없습니다.", "クラッシュ！ログを保存できませんでした。", "崩溃！无法保存日志。",
                "¡CRASH! No se pudo guardar el registro.", "LỖI! Không thể lưu nhật ký.", "CRASH! Tidak dapat menyimpan log.", "FALHA! Não foi possível salvar o log.");
        addComplete("Default On Child",
                "Default sa Bata", "자식에게 기본값", "子供にデフォルト", "对子对象默认",
                "Predeterminado en niño", "Mặc định Trên Trẻ Em", "Default pada Anak", "Padrão na Criança");
        addComplete("Default On Testing Child",
                "Default sa Pagsusuri ng Bata", "테스트 자식에게 기본값", "テスト中の子供にデフォルト", "对子对象测试默认",
                "Predeterminado en niño de prueba", "Mặc định Trên Trẻ Em Thử Nghiệm", "Default pada Anak yang Diuji", "Padrão na Criança de Teste");
        addComplete("Default On Testing Toggle",
                "Default sa Pagsusuri Toggle", "테스트 토글에 기본값", "テスト切り替えにデフォルト", "切换测试默认",
                "Predeterminado en alternar de prueba", "Mặc định Trên Chuyển Đổi Thử Nghiệm", "Default pada Toggle Pengujian", "Padrão no Alternar de Teste");
        addComplete("Default On Toggle",
                "Default sa Toggle", "토글에 기본값", "切り替えにデフォルト", "切换默认",
                "Predeterminado en alternar", "Mặc định Trên Chuyển Đổi", "Default pada Toggle", "Padrão no Alternar");
        addComplete("Default Open Collapse",
                "Default Buksan ang Buhayin", "열기/닫기 기본", "開く/折りたたむにデフォルト", "打开折叠默认",
                "Abrir colapso predeterminado", "Mặc định Mở Thu Gọn", "Buka Tutup Default", "Abrir/Recolher Padrão");
        addComplete("Delta",
                "Delta", "델타", "デルタ", "德尔塔",
                "Delta", "Delta", "Delta", "Delta");
        addComplete("Direct function example is disabled until its RVA is configured.",
                "Ang direktang halimbawa ng function ay hindi pinagana hangga't hindi nakasetup ang RVA nito.", "직접 함수 예제는 RVA가 구성될 때까지 비활성화됩니다.", "RVAが設定されるまで直接関数の例は無効です。", "在配置RVA之前，直接函数示例已禁用。",
                "El ejemplo de función directa está deshabilitado hasta que se configure su RVA.", "Ví dụ về chức năng trực tiếp bị vô hiệu cho đến khi RVA của nó được cấu hình.", "Contoh fungsi langsung dinonaktifkan hingga RVA-nya dikonfigurasi.", "Exemplo de função direta está desativado até que seu RVA seja configurado.");
        addComplete("Display Only Types",
                "Ipakita Lamang ang Uri", "유형만 표시", "タイプのみ表示", "仅显示类型",
                "Mostrar solo tipos", "Chỉ hiển thị các loại", "Hanya Tampilkan Tipe", "Exibir Apenas Tipos");
        addComplete("Epsilon",
                "Epsilon", "엡실론", "イプシロン", "埃普西龙",
                "Épsilon", "Epsilon", "Epsilon", "Epsilon");
        addComplete("Example Multi Select",
                "Halimbawa ng Maramihang Pagpili", "예제 다중 선택", "例：マルチセレクト", "示例多选",
                "Ejemplo de Selección Múltiple", "Ví dụ Chọn nhiều", "Contoh Pilih Banyak", "Exemplo de Seleção Múltipla");
        addComplete("Example Searchable Select",
                "Halimbawa ng Maaaring Hanapin na Pagpili", "예제 검색 가능한 선택", "例：検索可能セレクト", "示例可搜索选择",
                "Ejemplo de Selección Buscable", "Ví dụ Chọn có thể tìm kiếm", "Contoh Pilih yang Dapat Dicari", "Exemplo de Seleção Pesquisável");
        addComplete("Example Seek Bar",
                "Halimbawa ng Seek Bar", "예제 탐색 바", "例：シークバー", "示例滑动条",
                "Ejemplo de Barra de Búsqueda", "Ví dụ Thanh trượt tìm kiếm", "Contoh Bilah Pencarian", "Exemplo de Barra de Busca");
        addComplete("Example Spinner",
                "Halimbawa ng Spinner", "예제 스피너", "例：スピナー", "示例旋转器",
                "Ejemplo de Selector Giratorio", "Ví dụ Spinner", "Contoh Pemutar", "Exemplo de Spinner");
        addComplete("Explicit Positive ID",
                "Eksplanadong Positibong ID", "명시적 양성 ID", "明示的な身元確認（Positive ID）", "明确正面身份识别",
                "Identificación Positiva Explícita", "ID Tích cực Rõ ràng", "ID Positif Eksplisit", "Identificação Positiva Explícita");
        addComplete("Float Input With Maximum",
                "Float Input na may Maximum", "최대값으로 부동 입력", "最大値付き浮動入力", "带最大值的浮点输入",
                "Entrada de número flotante con máximo", "Nhập số thực với giới hạn tối đa", "Masukan Float Dengan Maksimum", "Entrada de Float com Máximo");
        addComplete("Float Input Without Maximum",
                "Float Input na walang Maximum", "최대값 없는 부동 입력", "最大値なし浮動入力", "不带最大值的浮点输入",
                "Entrada de número flotante sin máximo", "Nhập số thực không có giới hạn tối đa", "Masukan Float Tanpa Maksimum", "Entrada de Float sem Máximo");
        addComplete("Free Everything",
                "Libreng Lahat", "모든 것 무료", "無料すべて", "一切免费",
                "Todo Gratis", "Mọi Thứ Miễn Phí", "Semuanya Gratis", "Tudo Grátis");
        addComplete("Gamma",
                "Gamma", "감마", "ガンマ", "伽马",
                "Gamma", "Gamma", "Gamma", "Gama");
        addComplete("Grouped Amount",
                "Pinagsamang Dami", "그룹 수량", "グループ化された数量", "分组数量",
                "Cantidad Agrupada", "Số lượng nhóm", "Jumlah Terkelompok", "Quantidade Agrupada");
        addComplete("Grouped Mode",
                "Pinagsamang Mode", "그룹 모드", "グループ化モード", "分组模式",
                "Modo Agrupado", "Chế độ nhóm", "Mode Terkelompok", "Modo Agrupado");
        addComplete("Guest",
                "Panauhin", "게스트", "ゲスト", "访客",
                "Invitado", "Khách", "Tamu", "Convidado");
        addComplete("Hide",
                "Itago", "숨기기", "非表示", "隐藏",
                "Ocultar", "Ẩn", "Sembunyikan", "Esconder");
        addComplete("Hook example is unavailable for this binary.",
                "Ang halimbawa ng Hook ay hindi available para sa binary na ito.", "이 바이너리에서는 훅 예제가 사용할 수 없습니다.", "このバイナリではフックの例は利用できません。", "此二进制文件不提供挂钩示例。",
                "El ejemplo de gancho no está disponible para este binario.", "Ví dụ móc không khả dụng cho bản nhị phân này.", "Contoh hook tidak tersedia untuk binary ini.", "Exemplo de gancho indisponível para este binário.");
        addComplete("Input Types",
                "Mga Uri ng Input", "입력 유형", "入力タイプ", "输入类型",
                "Tipos de Entrada", "Các Loại Nhập", "Jenis Input", "Tipos de Entrada");
        addComplete("In-run Coins & Mode Currency Multiplier (0-1 = normal)",
                "Multiplier ng Coins sa Paglalaro at Pera ng Mode (0-1 = normal)", "인런 코인 & 모드 화폐 배수 (0-1 = 정상)", "ラン中のコイン & モード通貨倍率 (0-1 = 通常)", "运行中的硬币和模式货币倍数（0-1 = 正常）",
                "Multiplicador de Monedas y Moneda de Modo en la Carrera (0-1 = normal)", "Nhân Tiền & Tiền Chế Độ Khi Chạy (0-1 = bình thường)", "Koin & Pengganda Mata Uang Mode saat Berlari (0-1 = normal)", "Multiplicador de Moedas em Corrida & Moeda do Modo (0-1 = normal)");
        addComplete("Integer Input With Maximum",
                "Integer Input na may Maximum", "최대값 포함 정수 입력", "最大値付き整数入力", "带最大值的整数输入",
                "Entrada de Entero Con Máximo", "Nhập Số Nguyên Có Giới Hạn", "Input Bilangan Bulat Dengan Maksimum", "Entrada Inteira com Máximo");
        addComplete("Integer Input Without Maximum",
                "Integer Input na walang Maximum", "최대값 없는 정수 입력", "最大値なし整数入力", "不带最大值的整数输入",
                "Entrada de Entero Sin Máximo", "Nhập Số Nguyên Không Giới Hạn", "Input Bilangan Bulat Tanpa Maksimum", "Entrada Inteira sem Máximo");
        addComplete("Item Receive Multipliers",
                "Multiplikador sa pagtanggap ng item", "아이템 수령 배수", "アイテム受取倍率", "物品接收倍数",
                "Multiplicadores de recepción de objetos", "Nhân số nhận vật phẩm", "Pengganda Penerimaan Barang", "Multiplicadores de recebimento de itens");
        addComplete("Long Input With Maximum",
                "Mahabang Input na may Maximum", "최대 입력이 있는 긴 입력", "最大で長い入力", "带最大值的长输入",
                "Entrada larga con máximo", "Nhập Liệu Dài Với Tối Đa", "Input Panjang Dengan Maksimum", "Entrada Longa com Máximo");
        addComplete("Long Input Without Maximum",
                "Mahabang Input na walang Maximum", "최대 입력이 없는 긴 입력", "最大でない長い入力", "不带最大值的长输入",
                "Entrada larga sin máximo", "Nhập Liệu Dài Không Có Tối Đa", "Input Panjang Tanpa Maksimum", "Entrada Longa sem Máximo");
        addComplete("Materials & Event Items Multiplier (0-1 = normal)",
                "Multiplier ng Mga Materyales at Item sa Kaganapan (0-1 = normal)", "재료 및 이벤트 아이템 배율 (0-1 = 일반)", "素材＆イベントアイテムの倍率（0-1 = 通常）", "材料及活动道具倍数（0-1=正常）",
                "Multiplicador de materiales y objetos de evento (0-1 = normal)", "Hệ số nhân Nguyên liệu & Vật phẩm Sự kiện (0-1 = bình thường)", "Pengganda Bahan & Item Event (0-1 = normal)", "Multiplicador de Materiais e Itens de Evento (0-1 = normal)");
        addComplete("Max Level All Bodyguards",
                "Max Antas ng Lahat ng Bodyguard", "모든 경호원 최대 레벨", "すべてのボディーガードのレベルを最大化", "所有保镖的等级已最大化",
                "Maximizar nivel de todos los guardaespaldas", "Tối đa cấp độ tất cả vệ sĩ", "Maks Level Semua Pengawal", "Maximizar Todos os Guarda-costas");
        addComplete("Menu by Jester",
                "Menu ni Jester", "조커의 메뉴", "ジェスターによるメニュー", "小丑的菜单",
                "Menú por Jester", "Menu bởi Jester", "Menu oleh Jester", "Menu por Jester");
        addComplete("Native Implementation Examples",
                "Mga Halimbawa ng Nakaprogmang Katutubong Implementasyon", "네이티브 구현 예제", "ネイティブ実装例", "本地实现示例",
                "Ejemplos de Implementación Nativa", "Ví dụ về Triển khai Gốc", "Contoh Implementasi Bawaan", "Exemplos de Implementação Nativa");
        addComplete("Nested Child Button",
                "Naka-nest na Batang Button", "중첩된 하위 버튼", "ネストされた子ボタン", "嵌套子按钮",
                "Botón Hijo Anidado", "Nút Con Lồng Nhau", "Tombol Anak Bersarang", "Botão Filho Aninhado");
        addComplete("Nested child button pressed.",
                "Pindot ang naka-nest na batang button.", "중첩된 하위 버튼이 눌림.", "ネストされた子ボタンが押されました。", "嵌套子按钮已按下。",
                "Botón hijo anidado presionado.", "Nút con lồng nhau đã được nhấn.", "Tombol anak bersarang ditekan.", "Botão filho aninhado pressionado.");
        addComplete("No ARM32 patch example is configured.",
                "Walang halimbawa ng ARM32 patch ang nakatakda.", "ARM32 패치 예제가 구성되지 않음.", "ARM32パッチ例は構成されていません。", "未配置 ARM32 补丁示例。",
                "No se ha configurado ejemplo de parche ARM32.", "Không có ví dụ vá ARM32 được cấu hình.", "Tidak ada contoh patch ARM32 yang dikonfigurasi.", "Nenhum exemplo de patch ARM32 está configurado.");
        addComplete("One",
                "Isa", "하나", "1", "一",
                "Uno", "Một", "Satu", "Um");
        addComplete("Ordinary Button",
                "Karaniwang Button", "일반 버튼", "普通のボタン", "普通按钮",
                "Botón ordinario", "Nút Thường", "Tombol Biasa", "Botão Comum");
        addComplete("Ordinary Button pressed.",
                "Pindot sa Karaniwang Button.", "일반 버튼 눌림.", "普通のボタンが押されました", "普通按钮 已按下。",
                "Botón ordinario presionado.", "Nút Thường đã nhấn.", "Tombol Biasa ditekan.", "Botão Comum pressionado.");
        addComplete("Overlay permission is required in order to show mod menu.",
                "Kinakailangan ang pahintulot sa overlay upang ipakita ang mod menu.", "모드 메뉴를 표시하려면 오버레이 권한이 필요합니다.", "モッドメニューを表示するにはオーバーレイの許可が必要です", "显示mod菜单需要覆盖权限。",
                "Se requiere permiso de superposición para mostrar el menú de mod.", "Cần quyền cấp phủ để hiển thị menu mod.", "Izin overlay diperlukan untuk menampilkan menu mod.", "Permissão de sobreposição é necessária para exibir o menu de mods.");
        addComplete("Parent collapse button pressed.",
                "Pindot sa button upang itago ang magulang.", "부모 접기 버튼 눌림.", "親折りたたみボタンが押されました", "父项折叠按钮 已按下。",
                "Botón de colapso del padre presionado.", "Nút thu gọn cha đã nhấn.", "Tombol lipat induk ditekan.", "Botão de colapso dos pais pressionado.");
        addComplete("Patch Example",
                "Halimbawang Patch", "패치 예제", "パッチ例", "补丁示例",
                "Ejemplo de parche", "Ví Dụ Bản Vá", "Contoh Patch", "Exemplo de Patch");
        addComplete("Prefix and State Examples",
                "Mga Halimbawa ng Prefix at Estado", "접두사 및 상태 예시", "接頭辞と状態の例", "前缀和状态示例",
                "Ejemplos de prefijo y estado", "Ví dụ về Tiền tố và Trạng thái", "Contoh Awalan dan Status", "Exemplos de Prefixo e Estado");
        addComplete("Primary Action Button",
                "Pangunahing Button ng Aksyon", "주 행동 버튼", "メインアクションボタン", "主要操作按钮",
                "Botón de acción primaria", "Nút Hành động Chính", "Tombol Aksi Utama", "Botão de Ação Primária");
        addComplete("Primary Action Button pressed.",
                "Pinindot ang Pangunahing Button ng Aksyon.", "주 행동 버튼 눌림.", "メインアクションボタンが押されました。", "主要操作按钮已按下。",
                "Botón de acción primaria presionado.", "Nút Hành động Chính đã được nhấn.", "Tombol Aksi Utama ditekan.", "Botão de Ação Primária pressionado.");
        addComplete("Seeds Multiplier (0-1 = normal)",
                "Multiplier ng Butil (0-1 = normal)", "시드 배수 (0-1 = 정상)", "種の倍率（0-1 = 通常）", "种子倍数（0-1 = 正常）",
                "Multiplicador de Semillas (0-1 = normal)", "Hệ số nhân Hạt giống (0-1 = bình thường)", "Pengali Biji (0-1 = normal)", "Multiplicador de Sementes (0-1 = normal)");
        addComplete("Signed Negative ID",
                "Nilagda na Negatibong ID", "음수 ID 서명", "署名済み負ID", "签署负ID",
                "ID Negativo Firmado", "ID âm đã ký", "ID Negatif Ditandatangani", "ID Negativo Assinado");
        addComplete("Special Event Tickets Multiplier (0-1 = normal)",
                "Multiplier ng Tiket para sa Espesyal na Kaganapan (0-1 = normal)", "특별 이벤트 티켓 배수 (0-1 = 정상)", "特別イベントチケット乗数（0-1 = 通常）", "特殊活动票倍数（0-1=正常）",
                "Multiplicador de tickets de evento especial (0-1 = normal)", "Hệ số nhân Vé Sự kiện đặc biệt (0-1 = bình thường)", "Pengganda Tiket Acara Khusus (0-1 = normal)", "Multiplicador de Ingressos de Evento Especial (0-1 = normal)");
        addComplete("Standalone Callback Controls",
                "Mga Standalone na Kontrol ng Callback", "독립형 콜백 컨트롤", "スタンドアロンコールバックコントロール", "独立回调控件",
                "Controles de Retrollamada Independientes", "Điều Khiển Lời Gọi Riêng Lẻ", "Kontrol Callback Mandiri", "Controles de Callback Independentes");
        addComplete("SYSTEM STATUS",
                "KALAGAYAN NG SISTEMA", "시스템 상태", "システムステータス", "系统状态",
                "ESTADO DEL SISTEMA", "TRẠNG THÁI HỆ THỐNG", "STATUS SISTEM", "STATUS DO SISTEMA");
        addComplete("Template patch is disabled. Replace the placeholder RVA first.",
                "Ang template patch ay hindi pinagana. Palitan muna ang placeholder na RVA.", "템플릿 패치가 비활성화되었습니다. 먼저 플레이스홀더 RVA를 교체하세요.", "テンプレートパッチは無効です。まずプレースホルダーRVAを置き換えてください。", "模板补丁已禁用。请先替换占位符 RVA。",
                "El parche de plantilla está deshabilitado. Primero reemplace el RVA del marcador de posición.", "Bản vá mẫu bị vô hiệu hóa. Trước tiên hãy thay thế RVA giữ chỗ.", "Patch template dinonaktifkan. Ganti placeholder RVA terlebih dahulu.", "O patch do template está desativado. Substitua primeiro o RVA provisório.");
        addComplete("Testing Toggle",
                "Pag-toggle ng Pagsubok", "테스트 토글", "テスト切り替え", "测试开关",
                "Interruptor de prueba", "Chuyển Đổi Kiểm Tra", "Pengalihan Pengujian", "Alternância de Teste");
        addComplete("Text Input Without Default",
                "Pag-input ng Teksto nang Walang Default", "기본값 없는 텍스트 입력", "デフォルトなしのテキスト入力", "无默认文本输入",
                "Entrada de texto sin predeterminado", "Nhập Văn bản Không Có Mặc định", "Masukan Teks Tanpa Default", "Entrada de Texto sem Padrão");
        addComplete("Three",
                "Tatlo", "셋", "三", "三",
                "Tres", "Ba", "Tiga", "Três");
        addComplete("Token Tickets Multiplier (0-1 = normal)",
                "Multiplikador ng Token Tickets (0-1 = normal)", "토큰 티켓 배수 (0-1 = 보통)", "トークンチケット倍率（0-1=通常）", "代币票倍数（0-1 = 正常）",
                "Multiplicador de boletos de token (0-1 = normal)", "Nhân Vé Token (0-1 = bình thường)", "Pengali Tiket Token (0-1 = normal)", "Multiplicador de Bilhetes de Token (0-1 = normal)");
        addComplete("Two",
                "Dalawa", "둘", "二", "二",
                "Dos", "Hai", "Dua", "Dois");
        addComplete("Weapon Projectile & Effect Size Multiplier (1x-10x)",
                "Multiplier ng Laki ng Proyektil at Epekto ng Sandata (1x-10x)", "무기 투사체 및 효과 크기 배율 (1x-10x)", "武器の弾道とエフェクトサイズ倍率（1x-10x）", "武器投射物和效果大小倍增器（1x-10x）",
                "Multiplicador de tamaño de proyectil y efecto del arma (1x-10x)", "Bộ nhân Kích thước Đạn đạo & Hiệu ứng Vũ khí (1x-10x)", "Pengganda Ukuran Proyektil & Efek Senjata (1x-10x)", "Multiplicador de tamanho de projétil e efeito de arma (1x-10x)");
        // END GENERATED COMPLETE MODULE TRANSLATIONS

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
        addArabic("<font color='#E8B86A'>Return to features</font>", "<font color='#E8B86A'>الرجوع إلى الميزات</font>");
        addArabic("All Options", "جميع الخيارات");
        addArabic("Alpha", "ألفا");
        addArabic("Apply Grouped Values", "تطبيق القيم المجمعة");
        addArabic("Arabic", "العربية");
        addArabic("Attack Speed Multiplier (1x-10x)", "مضاعفة سرعة الهجوم (1x-10x)");
        addArabic("Automatic ID Example", "مثال على المعرف التلقائي");
        addArabic("Back In Parent Collapse", "العودة إلى انهيار الوالدين");
        addArabic("Basic Toggle", "تبديل أساسي");
        addArabic("Beta", "بيتا");
        addArabic("Bodyguard max-level enabled. Open or refresh the Bodyguard page once.", "تم تمكين المستوى الأقصى للحارس الشخصي. افتح أو قم بتحديث صفحة Bodyguard مرة واحدة.");
        addArabic("Checked automatically when the game library is available.", "يتم التحقق تلقائيًا عندما تكون مكتبة الألعاب متاحة.");
        addArabic("Checking game compatibility...\n\nIf the game library loads late, the menu will continue automatically.", "جارٍ التحقق من توافق اللعبة...\n\nإذا تم تحميل مكتبة الألعاب في وقت متأخر، فستستمر القائمة تلقائيًا.");
        addArabic("Checking game library", "التحقق من مكتبة الألعاب");
        addArabic("Collapse Child Toggle", "طي تبديل الطفل");
        addArabic("Connected group action pressed.", "تم الضغط على إجراء المجموعة المتصلة.");
        addArabic("CRASH! check:", "تحطم! تحقق:");
        addArabic("CRASH! Could not save log.", "تحطم! لا يمكن حفظ السجل.");
        addArabic("Default On Child", "الافتراضي على الطفل");
        addArabic("Default On Testing Child", "الافتراضي عند اختبار الطفل");
        addArabic("Default On Testing Toggle", "الافتراضي عند تبديل الاختبار");
        addArabic("Default On Toggle", "الافتراضي عند التبديل");
        addArabic("Default Open Collapse", "الافتراضي فتح الانهيار");
        addArabic("Delta", "دلتا");
        addArabic("Direct function example is disabled until its RVA is configured.", "تم تعطيل مثال الوظيفة المباشرة حتى يتم تكوين RVA الخاص به.");
        addArabic("Disable", "تعطيل");
        addArabic("Disabled", "معطّل");
        addArabic("Display Only Types", "عرض الأنواع فقط");
        addArabic("Enable", "تفعيل");
        addArabic("Enabled", "مفعّل");
        addArabic("Epsilon", "إبسيلون");
        addArabic("Example Multi Select", "مثال متعدد التحديد");
        addArabic("Example Searchable Select", "مثال قابل للبحث اختر");
        addArabic("Example Seek Bar", "مثال شريط البحث");
        addArabic("Example Spinner", "مثال سبينر");
        addArabic("Explicit Positive ID", "معرف إيجابي صريح");
        addArabic("Float Input With Maximum", "تعويم المدخلات مع الحد الأقصى");
        addArabic("Float Input Without Maximum", "تعويم المدخلات دون الحد الأقصى");
        addArabic("Free Everything", "كل شيء مجاني");
        addArabic("Gamma", "جاما");
        addArabic("Grouped Amount", "المبلغ المجمع");
        addArabic("Grouped Mode", "الوضع المجمع");
        addArabic("Guest", "ضيف");
        addArabic("Hide", "إخفاء");
        addArabic("Hook example is unavailable for this binary.", "مثال الخطاف غير متوفر لهذا الثنائي.");
        addArabic("Input Types", "أنواع المدخلات");
        addArabic("In-run Coins & Mode Currency Multiplier (0-1 = normal)", "العملات المعدنية قيد التشغيل ومضاعف عملات الوضع (0-1 = عادي)");
        addArabic("Integer Input With Maximum", "إدخال عدد صحيح مع الحد الأقصى");
        addArabic("Integer Input Without Maximum", "إدخال عدد صحيح بدون الحد الأقصى");
        addArabic("Item Receive Multipliers", "البند تلقي المضاعفات");
        addArabic("Long Input With Maximum", "إدخال طويل مع الحد الأقصى");
        addArabic("Long Input Without Maximum", "إدخال طويل بدون الحد الأقصى");
        addArabic("Materials & Event Items Multiplier (0-1 = normal)", "مضاعف المواد وعناصر الحدث (0-1 = عادي)");
        addArabic("Max Level All Bodyguards", "الحد الأقصى لجميع الحراس الشخصيين");
        addArabic("Menu by Jester", "القائمة من قبل المهرج");
        addArabic("Native Implementation Examples", "أمثلة التنفيذ الأصلي");
        addArabic("Nested Child Button", "زر الطفل المتداخل");
        addArabic("Nested child button pressed.", "تم الضغط على زر الطفل المتداخل.");
        addArabic("No ARM32 patch example is configured.", "لم يتم تكوين أي مثال لتصحيح ARM32.");
        addArabic("OFF", "إيقاف");
        addArabic("ON", "تشغيل");
        addArabic("One", "واحد");
        addArabic("Ordinary Button", "زر عادي");
        addArabic("Ordinary Button pressed.", "تم الضغط على الزر العادي.");
        addArabic("Overlay permission is required in order to show mod menu.", "مطلوب إذن التراكب لإظهار قائمة التعديل.");
        addArabic("Parent collapse button pressed.", "تم الضغط على زر طي الوالدين.");
        addArabic("Patch Example", "مثال التصحيح");
        addArabic("Prefix and State Examples", "البادئة وأمثلة الدولة");
        addArabic("Primary Action Button", "زر الإجراء الأساسي");
        addArabic("Primary Action Button pressed.", "تم الضغط على زر الإجراء الأساسي.");
        addArabic("Seeds Multiplier (0-1 = normal)", "مضاعف البذور (0-1 = عادي)");
        addArabic("Signed Negative ID", "الهوية السلبية الموقعة");
        addArabic("Special Event Tickets Multiplier (0-1 = normal)", "مضاعف تذاكر الأحداث الخاصة (0-1 = عادي)");
        addArabic("Standalone Callback Controls", "ضوابط رد الاتصال المستقلة");
        addArabic("SYSTEM STATUS", "حالة النظام");
        addArabic("Template patch is disabled. Replace the placeholder RVA first.", "تم تعطيل تصحيح القالب. استبدل العنصر النائب RVA أولاً.");
        addArabic("Testing Toggle", "تبديل الاختبار");
        addArabic("Text Input Without Default", "إدخال النص بدون افتراضي");
        addArabic("Three", "ثلاثة");
        addArabic("Token Tickets Multiplier (0-1 = normal)", "مضاعف التذاكر الرمزية (0-1 = عادي)");
        addArabic("Two", "اثنان");
        addArabic("Weapon Projectile & Effect Size Multiplier (1x-10x)", "مقذوف السلاح ومضاعف حجم التأثير (1x-10x)");

    }

    private OfflineTranslator() {
    }

    public static void initialize(Context context) {
        if (context == null) return;
        Preferences preferences = Preferences.with(context);
        if (!preferences.contains(PREF_LANGUAGE)) {
            int launchLanguage = launchLanguage(context);
            if (launchLanguage >= ENGLISH) {
                preferences.writeInt(PREF_LANGUAGE, clampLanguage(launchLanguage));
                preferences.writeInt(PREF_LANGUAGE_SCHEMA, LANGUAGE_SCHEMA);
            }
        }
        int savedLanguage = preferences.readInt(PREF_LANGUAGE, ENGLISH);
        if (preferences.readInt(PREF_LANGUAGE_SCHEMA, 0) < LANGUAGE_SCHEMA) {
            savedLanguage = savedLanguage <= 1 ? ENGLISH : savedLanguage - 1;
            preferences.writeInt(PREF_LANGUAGE, savedLanguage);
            preferences.writeInt(PREF_LANGUAGE_SCHEMA, LANGUAGE_SCHEMA);
        }
        preferredLanguage = clampLanguage(savedLanguage);
    }

    private static int launchLanguage(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return ((Activity) current).getIntent()
                        .getIntExtra("com.moodtools.menu.LANGUAGE", -1);
            }
            Context base = ((ContextWrapper) current).getBaseContext();
            if (base == current) break;
            current = base;
        }
        return -1;
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
            if ("Spinner".equals(type) || "MultiSelectSpinner".equals(type)
                    || "MultiSelector".equals(type)) {
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
            String value = values[i];
            int start = 0;
            int end = value.length();
            while (start < end && Character.isWhitespace(value.charAt(start))) start++;
            while (end > start && Character.isWhitespace(value.charAt(end - 1))) end--;
            output.append(value, 0, start);
            output.append(tr(value.substring(start, end)));
            output.append(value, end, value.length());
        }
        return output.toString();
    }

    private static void add(String code, String[] translations) {
        if (translations.length != COMMON_ENGLISH.length) {
            throw new IllegalStateException("Translation table mismatch for " + code);
        }
        Map<String, String> language = new HashMap<>();
        for (int i = 0; i < COMMON_ENGLISH.length; i++) {
            if (i >= 5 && i <= 12) continue; // Keep language names native.
            language.put(COMMON_ENGLISH[i], translations[i]);
        }
        language.put("<font color='#E8B86A'>Return to features</font>",
                "<font color='#E8B86A'>" + translations[13] + "</font>");
        TRANSLATIONS.put(code, language);
    }

    private static void addComplete(String english, String filipino, String korean,
                                    String japanese, String chinese, String spanish,
                                    String vietnamese, String indonesian, String portuguese) {
        addLocalized(english, filipino, korean, japanese, chinese, spanish, vietnamese, indonesian);
        TRANSLATIONS.get("pt").put(english, portuguese);
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

    private static void addArabic(String english, String arabic) {
        TRANSLATIONS.get("ar").put(english, arabic);
    }

    private static int clampLanguage(int language) {
        return language >= ENGLISH && language <= 9 ? language : ENGLISH;
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
            case 8: return "pt";
            case 9: return "ar";
            default: return "en";
        }
    }
}
