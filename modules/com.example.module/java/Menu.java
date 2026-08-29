//Please don't replace listeners with lambda!

package com.android.support;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.method.DigitsKeyListener;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
import static android.widget.RelativeLayout.ALIGN_PARENT_LEFT;
import static android.widget.RelativeLayout.ALIGN_PARENT_RIGHT;

public class Menu {
    //********** Here you can easly change the menu appearance **********//
    public static final String TAG = "Mod_Menu"; //Tag for logcat

    int TEXT_COLOR = Color.parseColor("#F4F7FA");
    int TEXT_COLOR_2 = Color.parseColor("#D9E0E5");
    int TEXT_MUTED = Color.parseColor("#8E9AA3");
    int ACCENT_COLOR = Color.parseColor("#E8B86A");
    int SUCCESS_COLOR = Color.parseColor("#4CCB9B");
    int DANGER_COLOR = Color.parseColor("#D96C75");
    int TESTING_COLOR = Color.parseColor("#65C7D0");
    int TESTING_BG_COLOR = Color.parseColor("#FF193036");
    int TESTING_BORDER_COLOR = Color.parseColor("#FF3D858D");
    int BTN_COLOR = Color.parseColor("#27333C");
    int MENU_BG_COLOR = Color.parseColor("#F20F1418");
    int MENU_FEATURE_BG_COLOR = Color.parseColor("#F2181E24");
    int CONTROL_BG_COLOR = Color.parseColor("#FF202830");
    int PANEL_BORDER_COLOR = Color.parseColor("#FF3A4651");
    int DIVIDER_COLOR = Color.parseColor("#FF303A43");
    int MENU_WIDTH = 520;
    static final int MENU_FOOTER_HEIGHT_DP = 60;
    static final int MENU_CHROME_HEIGHT_DP = 152;
    static final int MENU_HEADER_HEIGHT_DP = 56;
    int lastScreenWidthPx, lastScreenHeightPx;
    int POS_X = 12;
    int POS_Y = 24;

    float MENU_CORNER = 8f;
    int ICON_SIZE = 50;
    float ICON_ALPHA = 0.82f;
    int ToggleON = SUCCESS_COLOR;
    int ToggleOFF = Color.parseColor("#5B6871");
    int BtnON = SUCCESS_COLOR;
    int BtnOFF = Color.parseColor("#49545D");
    int CategoryBG = Color.parseColor("#2A333B");
    int SeekBarColor = ACCENT_COLOR;
    int SeekBarProgressColor = ACCENT_COLOR;
    int CheckBoxColor = SUCCESS_COLOR;
    int RadioColor = ACCENT_COLOR;
    int CollapseColor = Color.parseColor("#242D35");
    String NumberTxtColor = "#E8B86A";
    final int[] MENU_PANEL_COLOR_FRAMES = {
            Color.parseColor("#F229210D"),
            Color.parseColor("#F22B1324"),
            Color.parseColor("#F21C1435"),
            Color.parseColor("#F20B2930"),
            Color.parseColor("#F229210D")
    };
    final int[] MENU_CONTENT_COLOR_FRAMES = {
            Color.parseColor("#F2282517"),
            Color.parseColor("#F22A1A27"),
            Color.parseColor("#F2201B31"),
            Color.parseColor("#F214292D"),
            Color.parseColor("#F2282517")
    };
    final int[] MENU_BORDER_COLOR_FRAMES = {
            Color.parseColor("#FFFFD166"),
            Color.parseColor("#FFFF5FA2"),
            Color.parseColor("#FFC18CFF"),
            Color.parseColor("#FF55E6E6"),
            Color.parseColor("#FFFFD166")
    };
    //********************************************************************//

    RelativeLayout mCollapsed, mRootContainer;
    LinearLayout mExpanded, mods, mSettings, mCollapse, mConnectedGroup;
    LinearLayout activeCollapse;
    TextView activeCollapseHeader, settingsButton, compatibilityStatusView;
    TextView hideButton, closeButton;
    String activeCollapseTitle;
    Stack<LinearLayout> mCollapseStack = new Stack<>();
    WindowManager mWindowManager;
    WindowManager.LayoutParams vmParams;
    ImageView startimage;
    FrameLayout rootFrame;
    ScrollView scrollView;
    boolean stopChecking, overlayRequired, menuAnimations, colorAnimations, settingsOpen;
    GradientDrawable menuBackgroundDrawable;
    ValueAnimator menuColorAnimator;
    final ArgbEvaluator menuColorEvaluator = new ArgbEvaluator();
    final Handler menuLoadHandler = new Handler(Looper.getMainLooper());
    final Handler compatibilityStatusHandler = new Handler(Looper.getMainLooper());
    Runnable menuLoadCheck, compatibilityStatusRefresh;
    String compatibilityStatusText;
    String detectedGameIdentityCache;
    Context getContext;

    //initialize methods from the native library
    native void Init(Context context, TextView title, TextView subTitle);

    native String Icon();

    native String IconWebViewData();

    native String[] GetFeatureList();

    native String[] SettingsList();

    native boolean IsGameLibLoaded();

    //Here we write the code for our Menu
    // Reference: https://www.androidhive.info/2016/11/android-floating-widget-like-facebook-chat-head/
    public Menu(Context context) {

        getContext = context;
        Preferences.context = context;
        OfflineTranslator.initialize(context);
        MENU_WIDTH = calculateMenuWidthDp();
        lastScreenWidthPx = context.getResources().getDisplayMetrics().widthPixels;
        lastScreenHeightPx = context.getResources().getDisplayMetrics().heightPixels;
        rootFrame = new FrameLayout(context); // Global markup
        rootFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int screenWidth = getContext.getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getContext.getResources().getDisplayMetrics().heightPixels;
                if (screenWidth != lastScreenWidthPx || screenHeight != lastScreenHeightPx) {
                    lastScreenWidthPx = screenWidth;
                    lastScreenHeightPx = screenHeight;
                    if (mExpanded != null && scrollView != null) {
                        ViewGroup.LayoutParams panelParams = mExpanded.getLayoutParams();
                        panelParams.height = WRAP_CONTENT;
                        mExpanded.setLayoutParams(panelParams);
                        scrollView.setLayoutParams(createMenuContentLayoutParams(Preferences.isExpanded));
                    }
                    scheduleFooterVisibilityGuard();
                }
            }
        });
        rootFrame.setOnTouchListener(onTouchListener());
        mRootContainer = new RelativeLayout(context); // Markup on which two markups of the icon and the menu itself will be placed
        mCollapsed = new RelativeLayout(context); // Markup of the icon (when the menu is minimized)
        mCollapsed.setVisibility(View.VISIBLE);
        mCollapsed.setAlpha(ICON_ALPHA);
        mCollapsed.setBackgroundColor(Color.TRANSPARENT);

        //********** The box of the mod menu **********
        mExpanded = new LinearLayout(context); // Menu markup (when the menu is expanded)
        mExpanded.setVisibility(View.GONE);
        mExpanded.setOrientation(LinearLayout.VERTICAL);
        mExpanded.setLayoutParams(new LinearLayout.LayoutParams(dp(MENU_WIDTH), WRAP_CONTENT));
        menuBackgroundDrawable = roundedBackground(MENU_BG_COLOR, MENU_CORNER, PANEL_BORDER_COLOR, 1);
        mExpanded.setBackground(menuBackgroundDrawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mExpanded.setElevation(dp(12));
        }

        //********** The icon to open mod menu **********
        startimage = new ImageView(context);
        startimage.setLayoutParams(new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        int applyDimension = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ICON_SIZE, context.getResources().getDisplayMetrics()); //Icon size
        startimage.getLayoutParams().height = applyDimension;
        startimage.getLayoutParams().width = applyDimension;
        //startimage.requestLayout();
        startimage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        startimage.setBackgroundColor(Color.TRANSPARENT);
        applyCircularClip(startimage);
        byte[] decode = Base64.decode(Icon(), 0);
        startimage.setImageBitmap(getCircularBitmap(BitmapFactory.decodeByteArray(decode, 0, decode.length)));
        ((ViewGroup.MarginLayoutParams) startimage.getLayoutParams()).topMargin = convertDipToPixels(10);
        //Initialize event handlers for buttons, etc.
        startimage.setOnTouchListener(onTouchListener());
        startimage.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                showExpandedMenu();
            }
        });

        //********** The icon in Webview to open mod menu **********
        // Avoid initializing Chromium for the normal bitmap-icon path. Some virtual Android
        // runtimes cannot safely tear down an otherwise-unused WebView during guest shutdown.
        String iconWebViewData = IconWebViewData();
        WebView wView = null;
        if (iconWebViewData != null) {
            wView = new WebView(context); //Icon size width=\"50\" height=\"50\"
            wView.setLayoutParams(new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
            int applyDimension2 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ICON_SIZE, context.getResources().getDisplayMetrics()); //Icon size
            wView.getLayoutParams().height = applyDimension2;
            wView.getLayoutParams().width = applyDimension2;
            wView.loadData("<html>" +
                    "<head><style>html,body{background:transparent;margin:0;padding:0;overflow:hidden;}img{border-radius:50%;display:block;}</style></head>" +
                    "<body>" +
                    "<img src=\"" + iconWebViewData + "\" width=\"" + ICON_SIZE + "\" height=\"" + ICON_SIZE + "\">" +
                    "</body>" +
                    "</html>", "text/html", "utf-8");
            wView.setBackgroundColor(0x00000000); //Transparent
            applyCircularClip(wView);
            wView.setAlpha(ICON_ALPHA);
            wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            wView.setOnTouchListener(onTouchListener());
        }

        //********** Settings command **********
        settingsButton = new TextView(context);
        settingsButton.setText(OfflineTranslator.tr("SETTINGS"));
        settingsButton.setContentDescription(OfflineTranslator.tr("Open menu settings"));
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setTextColor(ACCENT_COLOR);
        settingsButton.setTypeface(Typeface.DEFAULT_BOLD);
        settingsButton.setTextSize(11.0f);
        settingsButton.setMinWidth(dp(76));
        settingsButton.setPadding(dp(12), 0, dp(12), 0);
        settingsButton.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, PANEL_BORDER_COLOR, 1));
        RelativeLayout.LayoutParams rlsettings = new RelativeLayout.LayoutParams(WRAP_CONTENT, dp(36));
        rlsettings.addRule(ALIGN_PARENT_RIGHT);
        rlsettings.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        rlsettings.topMargin = dp(10);
        settingsButton.setLayoutParams(rlsettings);
        attachPressAnimation(settingsButton);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    settingsOpen = !settingsOpen;
                    if (settingsOpen) {
                        showMenuContent(mSettings);
                    } else {
                        showMenuContent(mods);
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        });

        //********** Settings **********
        mSettings = new LinearLayout(context);
        mSettings.setOrientation(LinearLayout.VERTICAL);
        featureList(SettingsList(), mSettings);

        //********** Title **********
        RelativeLayout titleText = new RelativeLayout(context);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(MENU_HEADER_HEIGHT_DP)));
        titleText.setPadding(dp(16), 0, dp(12), 0);
        titleText.setVerticalGravity(16);
        titleText.setClipChildren(false);
        titleText.setClipToPadding(false);

        TextView title = new TextView(context);
        title.setTextColor(TEXT_COLOR);
        title.setTextSize(19.0f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams rl = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        rl.addRule(ALIGN_PARENT_LEFT);
        rl.addRule(RelativeLayout.CENTER_VERTICAL);
        rl.rightMargin = dp(88);
        title.setLayoutParams(rl);

        //********** Sub title **********
        TextView subTitle = new TextView(context);
        subTitle.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        subTitle.setMarqueeRepeatLimit(-1);
        subTitle.setSingleLine(true);
        subTitle.setSelected(true);
        subTitle.setTextColor(TEXT_MUTED);
        subTitle.setTextSize(11.0f);
        subTitle.setGravity(Gravity.START);
        subTitle.setPadding(dp(16), 0, dp(16), dp(10));

        //********** Mod menu feature list **********
        scrollView = new AdaptiveMenuScrollView(context);
        // Size to the visible content until the safe screen-relative limit is reached.
        scrollView.setLayoutParams(createMenuContentLayoutParams(Preferences.isExpanded));
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(10), dp(8), dp(10), dp(8));
        scrollView.setBackgroundColor(MENU_FEATURE_BG_COLOR);
        mods = new LinearLayout(context);
        mods.setOrientation(LinearLayout.VERTICAL);
        mods.setPadding(0, 0, 0, dp(12));

        //********** Footer commands **********
        LinearLayout relativeLayout = new LinearLayout(context);
        relativeLayout.setLayoutParams(new LinearLayout.LayoutParams(
                MATCH_PARENT, dp(MENU_FOOTER_HEIGHT_DP)));
        relativeLayout.setOrientation(LinearLayout.HORIZONTAL);
        relativeLayout.setGravity(Gravity.CENTER_VERTICAL);
        relativeLayout.setPadding(dp(10), dp(8), dp(10), dp(8));
        relativeLayout.setMinimumHeight(dp(MENU_FOOTER_HEIGHT_DP));
        relativeLayout.setBackgroundColor(MENU_FEATURE_BG_COLOR);

        //**********  Hide/Kill button **********
        LinearLayout.LayoutParams lParamsHideBtn = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        lParamsHideBtn.rightMargin = dp(5);

        hideButton = new TextView(context);
        hideButton.setLayoutParams(lParamsHideBtn);
        hideButton.setMinWidth(0);
        hideButton.setMinimumWidth(0);
        hideButton.setMinHeight(dp(44));
        hideButton.setMinimumHeight(dp(44));
        hideButton.setText(OfflineTranslator.tr("Hide"));
        hideButton.setTextSize(12f);
        hideButton.setSingleLine(false);
        hideButton.setMaxLines(2);
        hideButton.setIncludeFontPadding(false);
        hideButton.setGravity(Gravity.CENTER);
        hideButton.setPadding(dp(6), dp(4), dp(6), dp(4));
        hideButton.setTextColor(TEXT_MUTED);
        hideButton.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, PANEL_BORDER_COLOR, 1));
        attachPressAnimation(hideButton);
        hideButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mCollapsed.setVisibility(View.VISIBLE);
                mCollapsed.setAlpha(0);
                mExpanded.setVisibility(View.GONE);
                Main.SetMenuExpanded(false);
                updateColorAnimationState();
                Main.ShowNativeToast(view.getContext(),
                        "Icon hidden. Remember the hidden icon position", Toast.LENGTH_LONG);
            }
        });
        hideButton.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View view) {
                mCollapsed.setVisibility(View.VISIBLE);
                mCollapsed.setAlpha(0);
                mExpanded.setVisibility(View.GONE);
                Main.SetMenuExpanded(false);
                updateColorAnimationState();
                Main.ShowNativeToast(view.getContext(),
                        "Icon hidden. Remember the hidden icon position", Toast.LENGTH_LONG);
                return true;
            }
        });

        //********** Close button **********
        LinearLayout.LayoutParams lParamsCloseBtn = new LinearLayout.LayoutParams(
                0, dp(44), 1f);
        lParamsCloseBtn.leftMargin = dp(5);

        closeButton = new TextView(context);
        closeButton.setLayoutParams(lParamsCloseBtn);
        closeButton.setMinWidth(0);
        closeButton.setMinimumWidth(0);
        closeButton.setMinHeight(dp(44));
        closeButton.setMinimumHeight(dp(44));
        closeButton.setText(OfflineTranslator.tr("Minimize"));
        closeButton.setTextSize(12f);
        closeButton.setSingleLine(false);
        closeButton.setMaxLines(2);
        closeButton.setIncludeFontPadding(false);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setPadding(dp(6), dp(4), dp(6), dp(4));
        closeButton.setTextColor(TEXT_COLOR);
        closeButton.setBackground(roundedBackground(BTN_COLOR, 6, PANEL_BORDER_COLOR, 1));
        attachPressAnimation(closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mCollapsed.setVisibility(View.VISIBLE);
                mCollapsed.setAlpha(ICON_ALPHA);
                mExpanded.setVisibility(View.GONE);
                Main.SetMenuExpanded(false);
                updateColorAnimationState();
            }
        });

        //********** Adding view components **********
        mRootContainer.addView(mCollapsed);
        mRootContainer.addView(mExpanded);
        if (wView != null) {
            mCollapsed.addView(wView);
        } else {
            mCollapsed.addView(startimage);
        }
        titleText.addView(title);
        titleText.addView(settingsButton);
        mExpanded.addView(titleText);
        mExpanded.addView(subTitle);
        scrollView.addView(mods);
        mExpanded.addView(scrollView);
        relativeLayout.addView(hideButton);
        relativeLayout.addView(closeButton);
        mExpanded.addView(relativeLayout);

        Init(context, title, subTitle);
        applyDetectedGameHeader(title);
    }

    public void ShowMenu() {
        if (mRootContainer.getParent() == null) {
            rootFrame.addView(mRootContainer);
        }

        if (menuLoadCheck != null) {
            menuLoadHandler.removeCallbacks(menuLoadCheck);
        }
        menuLoadCheck = new Runnable() {
            boolean viewLoaded = false;

            @Override
            public void run() {
                // Wait for a terminal native compatibility result so every user sees
                // whether the target library and configured hooks are supported.
                if (!IsGameLibLoaded() && !stopChecking) {
                    if (!viewLoaded) {
                        Category(mods, OfflineTranslator.tr("Checking game compatibility...\n\nIf the game library loads late, the menu will continue automatically."));
                        Button(mods, -100, OfflineTranslator.tr("Force load menu"));
                        viewLoaded = true;
                    }
                    menuLoadHandler.postDelayed(this, 600);
                } else {
                    mods.removeAllViews();
                    activeCollapse = null;
                    activeCollapseHeader = null;
                    activeCollapseTitle = null;
                    featureList(GetFeatureList(), mods);
                    scrollView.post(new Runnable() {
                        @Override
                        public void run() {
                            scrollView.fullScroll(View.FOCUS_UP);
                        }
                    });
                }
            }
        };
        menuLoadHandler.postDelayed(menuLoadCheck, 500);
    }

    private LinearLayout.LayoutParams createMenuContentLayoutParams(boolean expanded) {
        if (scrollView instanceof AdaptiveMenuScrollView) {
            ((AdaptiveMenuScrollView) scrollView).setExpandedHeight(expanded);
        }
        return new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
    }

    private int calculateMenuContentHeightDp(boolean expanded) {
        float density = getContext.getResources().getDisplayMetrics().density;
        int screenHeightDp = Math.round(calculateAvailableWindowHeightPx() / density);
        int available = Math.max(112, screenHeightDp - MENU_CHROME_HEIGHT_DP - 12);
        // Let long content use the full safe visible height. AT_MOST measurement still keeps
        // short menus compact, while the footer guard prevents the panel leaving the screen.
        return available;
    }

    private int calculateAvailableWindowHeightPx() {
        if (rootFrame != null && rootFrame.getWindowToken() != null) {
            android.graphics.Rect visibleFrame = new android.graphics.Rect();
            rootFrame.getWindowVisibleDisplayFrame(visibleFrame);
            if (visibleFrame.height() > 0) {
                return visibleFrame.height();
            }
        }
        return getContext.getResources().getDisplayMetrics().heightPixels;
    }

    private final class AdaptiveMenuScrollView extends ScrollView {
        private boolean expandedHeight;

        AdaptiveMenuScrollView(Context context) {
            super(context);
        }

        void setExpandedHeight(boolean expanded) {
            if (expandedHeight != expanded) {
                expandedHeight = expanded;
                requestLayout();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maximumHeight = dp(calculateMenuContentHeightDp(expandedHeight));
            int parentMode = MeasureSpec.getMode(heightMeasureSpec);
            if (parentMode != MeasureSpec.UNSPECIFIED) {
                maximumHeight = Math.min(maximumHeight, MeasureSpec.getSize(heightMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec,
                    MeasureSpec.makeMeasureSpec(Math.max(0, maximumHeight), MeasureSpec.AT_MOST));
        }
    }

    private int calculateMenuBoundsHeightDp(boolean expanded) {
        return calculateMenuContentHeightDp(expanded) + MENU_CHROME_HEIGHT_DP;
    }

    private int calculateMenuWidthDp() {
        float density = getContext.getResources().getDisplayMetrics().density;
        int screenWidthDp = Math.round(getContext.getResources().getDisplayMetrics().widthPixels / density);
        int screenHeightDp = Math.round(getContext.getResources().getDisplayMetrics().heightPixels / density);
        int horizontalMargin = 24;
        int target;

        if (screenWidthDp > screenHeightDp) {
            int sizingWidthDp = Math.min(screenWidthDp, 920);
            target = Math.round(sizingWidthDp * 0.58f);
            target = Math.max(430, Math.min(540, target));
        } else {
            int sizingWidthDp = Math.min(screenWidthDp, 440);
            target = sizingWidthDp - horizontalMargin;
        }
        int maximumWidth = Math.max(260, screenWidthDp - horizontalMargin);
        return Math.min(maximumWidth, Math.max(296, target));
    }

    private void applyMenuSize(boolean expanded) {
        MENU_WIDTH = calculateMenuWidthDp();
        ViewGroup.LayoutParams params = mExpanded.getLayoutParams();
        params.width = dp(MENU_WIDTH);
        params.height = WRAP_CONTENT;
        mExpanded.setLayoutParams(params);
        scrollView.setLayoutParams(createMenuContentLayoutParams(expanded));
        scheduleFooterVisibilityGuard();
    }

    private void scheduleFooterVisibilityGuard() {
        mExpanded.post(new Runnable() {
            @Override
            public void run() {
                guardFooterVisibility();
            }
        });
    }

    private void guardFooterVisibility() {
        if (mExpanded.getVisibility() != View.VISIBLE) {
            return;
        }

        int screenHeight = calculateAvailableWindowHeightPx();
        int edgeMargin = dp(6);
        int maximumPanelHeight = Math.max(dp(120), screenHeight - (edgeMargin * 2));
        int panelHeight = Math.max(mExpanded.getHeight(), mExpanded.getMeasuredHeight());

        if (panelHeight > maximumPanelHeight) {
            ViewGroup.LayoutParams panelParams = mExpanded.getLayoutParams();
            panelParams.height = maximumPanelHeight;
            mExpanded.setLayoutParams(panelParams);

            // Preserve the header and footer; only the feature area gives up height.
            scrollView.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f));
            mExpanded.post(new Runnable() {
                @Override
                public void run() {
                    clampMenuPositionOnScreen();
                }
            });
            return;
        }

        clampMenuPositionOnScreen();
    }

    private void clampMenuPositionOnScreen() {
        if (vmParams == null || mWindowManager == null) {
            return;
        }

        boolean expanded = mExpanded != null && mExpanded.getVisibility() == View.VISIBLE;
        int viewWidth = dp(expanded ? MENU_WIDTH : ICON_SIZE);
        int estimatedHeight = dp(expanded ? calculateMenuBoundsHeightDp(Preferences.isExpanded) : ICON_SIZE + 10);
        int measuredHeight = expanded
                ? Math.max(mExpanded.getHeight(), mExpanded.getMeasuredHeight())
                : Math.max(rootFrame.getHeight(), rootFrame.getMeasuredHeight());
        int viewHeight = measuredHeight > 0 ? measuredHeight : estimatedHeight;
        int screenWidth = getContext.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext.getResources().getDisplayMetrics().heightPixels;
        int margin = dp(6);
        int minX = screenWidth >= viewWidth + (margin * 2) ? margin : 0;
        int minY = screenHeight >= viewHeight + (margin * 2) ? margin : 0;
        int maxX = Math.max(minX, screenWidth - viewWidth - minX);
        int maxY = Math.max(minY, screenHeight - viewHeight - minY);

        vmParams.x = Math.max(minX, Math.min(maxX, vmParams.x));
        vmParams.y = Math.max(minY, Math.min(maxY, vmParams.y));
        if (rootFrame.getParent() != null) {
            try {
                mWindowManager.updateViewLayout(rootFrame, vmParams);
            } catch (IllegalArgumentException ignored) {
            } catch (WindowManager.BadTokenException ignored) {
            }
        }
    }

    private GradientDrawable roundedBackground(int color, float radiusDp, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(Math.round(radiusDp)));
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private GradientDrawable controlDrawable(int color, float radiusDp, int widthDp, int heightDp) {
        GradientDrawable drawable = roundedBackground(color, radiusDp, Color.TRANSPARENT, 0);
        drawable.setSize(dp(widthDp), dp(heightDp));
        return drawable;
    }

    private StateListDrawable checkedDrawable(Drawable checked, Drawable unchecked) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_checked}, checked);
        states.addState(new int[]{}, unchecked);
        return states;
    }

    private Drawable explicitSeekTrack(int progressColor, int backgroundColor) {
        // Keep the track as the thin accent line used by the repacked menu.
        // A normal SeekBar/theme drawable expands this into a thick platform
        // bar when the menu is hosted by BlackBox.
        GradientDrawable background = roundedBackground(backgroundColor, 2, Color.TRANSPARENT, 0);
        background.setSize(1, dp(4));
        GradientDrawable progress = roundedBackground(progressColor, 2, Color.TRANSPARENT, 0);
        progress.setSize(1, dp(4));
        android.graphics.drawable.ClipDrawable clipped = new android.graphics.drawable.ClipDrawable(
                progress, Gravity.LEFT, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new Drawable[]{background, clipped});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        layers.setPadding(dp(1), 0, dp(1), 0);
        return layers;
    }

    private LinearLayout.LayoutParams featureLayoutParams(int topMarginDp, int bottomMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.setMargins(dp(2), dp(topMarginDp), dp(2), dp(bottomMarginDp));
        return params;
    }

    private void styleCommandButton(Button button, int backgroundColor) {
        button.setAllCaps(false);
        button.setTextColor(TEXT_COLOR);
        button.setTextSize(14f);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(46));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(roundedBackground(backgroundColor, 6, PANEL_BORDER_COLOR, 1));
        attachPressAnimation(button);
    }

    private void attachPressAnimation(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!menuAnimations) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().cancel();
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().cancel();
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
    }

    private void animateToggleFeedback(View view) {
        if (!menuAnimations) {
            return;
        }
        view.animate().cancel();
        view.setScaleX(0.99f);
        view.setScaleY(0.99f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void showExpandedMenu() {
        applyMenuSize(Preferences.isExpanded);
        mCollapsed.setVisibility(View.GONE);
        mExpanded.setVisibility(View.VISIBLE);
        Main.SetMenuExpanded(true);
        clampMenuPositionOnScreen();
        updateColorAnimationState();
        if (!menuAnimations) {
            mExpanded.setAlpha(1f);
            mExpanded.setScaleX(1f);
            mExpanded.setScaleY(1f);
            return;
        }
        mExpanded.animate().cancel();
        mExpanded.setAlpha(0f);
        mExpanded.setScaleX(0.975f);
        mExpanded.setScaleY(0.975f);
        mExpanded.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void showCollapseBody(View view) {
        view.animate().cancel();
        view.setVisibility(View.VISIBLE);
        if (!menuAnimations) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(-dp(6));
        view.animate().alpha(1f).translationY(0f).setDuration(170)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void hideCollapseBody(final View view) {
        view.animate().cancel();
        if (!menuAnimations) {
            view.setVisibility(View.GONE);
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.animate().alpha(0f).translationY(-dp(4)).setDuration(110).withEndAction(new Runnable() {
            @Override
            public void run() {
                view.setVisibility(View.GONE);
                view.setAlpha(1f);
                view.setTranslationY(0f);
            }
        }).start();
    }

    private void animateContentEntrance(View view) {
        if (!menuAnimations) {
            return;
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationX(dp(8));
        view.animate().alpha(1f).translationX(0f).setDuration(170)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void showMenuContent(final View content) {
        scrollView.removeAllViews();
        scrollView.scrollTo(0, 0);
        scrollView.addView(content);
        content.requestLayout();
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                // ScrollView may restore its old range after the new child is measured.
                // Reset again on the layout pass so the first Settings row is never clipped.
                scrollView.scrollTo(0, 0);
            }
        });
        animateContentEntrance(content);
    }

    private int evaluateMenuColor(float fraction, int[] colors) {
        float position = fraction * (colors.length - 1);
        int index = Math.min((int) position, colors.length - 2);
        return (Integer) menuColorEvaluator.evaluate(position - index, colors[index], colors[index + 1]);
    }

    private void updateColorAnimationState() {
        if (colorAnimations && mExpanded != null && mExpanded.getVisibility() == View.VISIBLE
                && rootFrame != null && rootFrame.getVisibility() == View.VISIBLE) {
            startMenuColorAnimation();
        } else {
            stopMenuColorAnimation();
        }
    }

    private void startMenuColorAnimation() {
        if (menuColorAnimator != null && menuColorAnimator.isRunning()) {
            return;
        }
        menuColorAnimator = ValueAnimator.ofFloat(0f, 1f);
        menuColorAnimator.setDuration(9000);
        menuColorAnimator.setRepeatCount(ValueAnimator.INFINITE);
        menuColorAnimator.setInterpolator(new LinearInterpolator());
        menuColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = (Float) animation.getAnimatedValue();
                menuBackgroundDrawable.setColor(evaluateMenuColor(fraction, MENU_PANEL_COLOR_FRAMES));
                menuBackgroundDrawable.setStroke(dp(2), evaluateMenuColor(fraction, MENU_BORDER_COLOR_FRAMES));
                scrollView.setBackgroundColor(evaluateMenuColor(fraction, MENU_CONTENT_COLOR_FRAMES));
            }
        });
        menuColorAnimator.start();
    }

    private void stopMenuColorAnimation() {
        if (menuColorAnimator != null) {
            menuColorAnimator.cancel();
            menuColorAnimator = null;
        }
        if (menuBackgroundDrawable != null) {
            menuBackgroundDrawable.setColor(MENU_BG_COLOR);
            menuBackgroundDrawable.setStroke(dp(1), PANEL_BORDER_COLOR);
        }
        if (scrollView != null) {
            scrollView.setBackgroundColor(MENU_FEATURE_BG_COLOR);
        }
    }

    private void styleSpinnerItem(TextView item, boolean dropdownItem) {
        item.setTextColor(dropdownItem ? TEXT_COLOR_2 : TEXT_COLOR);
        item.setTextSize(14f);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setMinHeight(dp(48));
        item.setPadding(dp(14), 0, dp(14), 0);
        item.setBackgroundColor(dropdownItem ? Color.parseColor("#FF202830") : Color.TRANSPARENT);
    }

    private interface TemplateSeekListener {
        void onValueChanged(int value, boolean fromUser);
    }

    private final class TemplateSeekBar extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int minValue;
        private int maxValue;
        private int value;
        private TemplateSeekListener listener;

        TemplateSeekBar(Context context) {
            super(context);
            setFocusable(true);
            setClickable(true);
        }

        void configure(int min, int max, int initial, TemplateSeekListener valueListener) {
            minValue = min;
            maxValue = Math.max(min + 1, max);
            listener = valueListener;
            setValue(initial, false);
        }

        void setValue(int nextValue, boolean fromUser) {
            int bounded = Math.max(minValue, Math.min(maxValue, nextValue));
            if (value != bounded || !fromUser) {
                value = bounded;
                invalidate();
                if (listener != null) listener.onValueChanged(value, fromUser);
            }
        }

        int getValue() {
            return value;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(30));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float radius = dp(9);
            float left = radius;
            float right = Math.max(left, getWidth() - radius);
            float centerY = getHeight() * 0.5f;
            paint.setColor(SeekBarProgressColor);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(left, centerY, right, centerY, paint);
            float fraction = (value - minValue) / (float) (maxValue - minValue);
            float thumbX = left + ((right - left) * fraction);
            paint.setColor(SeekBarColor);
            canvas.drawCircle(thumbX, centerY, radius, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getParent().requestDisallowInterceptTouchEvent(true);
                float radius = dp(9);
                float usable = Math.max(1f, getWidth() - (radius * 2f));
                float fraction = Math.max(0f, Math.min(1f, (event.getX() - radius) / usable));
                setValue(minValue + Math.round(fraction * (maxValue - minValue)), true);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private final class TemplateSwitch extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ArgbEvaluator colorEvaluator = new ArgbEvaluator();
        private boolean checked;
        private float checkedProgress;
        private ValueAnimator checkedAnimator;

        TemplateSwitch(Context context) {
            super(context);
            setClickable(false);
        }

        void setChecked(boolean value) {
            setChecked(value, false);
        }

        void setChecked(boolean value, boolean animate) {
            if (checked == value && checkedProgress == (value ? 1f : 0f)) {
                return;
            }
            checked = value;
            float targetProgress = value ? 1f : 0f;
            if (checkedAnimator != null) {
                checkedAnimator.cancel();
                checkedAnimator = null;
            }
            if (!animate) {
                checkedProgress = targetProgress;
                invalidate();
                return;
            }

            checkedAnimator = ValueAnimator.ofFloat(checkedProgress, targetProgress);
            checkedAnimator.setDuration(180);
            checkedAnimator.setInterpolator(new DecelerateInterpolator());
            checkedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    checkedProgress = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            checkedAnimator.start();
        }

        boolean isChecked() {
            return checked;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(42), dp(28));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerY = getHeight() * 0.5f;
            float trackLeft = dp(4);
            float trackRight = getWidth() - dp(4);
            float trackRadius = dp(7);
            paint.setColor((Integer) colorEvaluator.evaluate(
                    checkedProgress,
                    Color.parseColor("#FF35414A"),
                    Color.parseColor("#664CCB9B")));
            canvas.drawRoundRect(trackLeft, centerY - trackRadius, trackRight,
                    centerY + trackRadius, trackRadius, trackRadius, paint);
            float thumbRadius = dp(10);
            float thumbX = thumbRadius + ((getWidth() - (thumbRadius * 2f)) * checkedProgress);
            paint.setColor((Integer) colorEvaluator.evaluate(
                    checkedProgress,
                    Color.parseColor("#FF6C7881"),
                    ToggleON));
            canvas.drawCircle(thumbX, centerY, thumbRadius, paint);
        }

        @Override
        protected void onDetachedFromWindow() {
            if (checkedAnimator != null) {
                checkedAnimator.cancel();
                checkedAnimator = null;
            }
            super.onDetachedFromWindow();
        }
    }

    private Bitmap getCircularBitmap(Bitmap source) {
        if (source == null) {
            return null;
        }

        int size = Math.min(source.getWidth(), source.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        float dx = (size - source.getWidth()) * 0.5f;
        float dy = (size - source.getHeight()) * 0.5f;
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setTranslate(dx, dy);
        shader.setLocalMatrix(matrix);

        paint.setShader(shader);
        float radius = size * 0.5f;
        canvas.drawCircle(radius, radius, radius, paint);
        return output;
    }

    private void applyCircularClip(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }

        view.setClipToOutline(true);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int size = Math.min(view.getWidth(), view.getHeight());
                outline.setOval(0, 0, size, size);
            }
        });
    }

    @SuppressLint("WrongConstant")
    public void SetWindowManagerWindowService() {
        //Variable to check later if the phone supports Draw over other apps permission
        int iparams = Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O ? 2038 : 2002;
        vmParams = new WindowManager.LayoutParams(
                WRAP_CONTENT,
                WRAP_CONTENT,
                iparams,
                8 | FLAG_TRANSLUCENT_STATUS,
                -3);
        //params = new WindowManager.LayoutParams(WindowManager.LayoutParams.LAST_APPLICATION_WINDOW, 8, -3);
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = POS_Y;

        mWindowManager = (WindowManager) getContext.getSystemService(Context.WINDOW_SERVICE);
        ensureOverlayAttached();

        overlayRequired = true;
    }

    @SuppressLint("WrongConstant")
    public void SetWindowManagerActivity() {
        vmParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                POS_X,//initialX
                POS_Y,//initialy
                WindowManager.LayoutParams.TYPE_APPLICATION,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_OVERSCAN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSPARENT
        );
        vmParams.gravity = 51;
        vmParams.x = POS_X;
        vmParams.y = POS_Y;

        mWindowManager = ((Activity) getContext).getWindowManager();
        ensureOverlayAttached();
    }

    private void ensureOverlayAttached() {
        if (rootFrame == null || mWindowManager == null || vmParams == null) {
            return;
        }
        if (rootFrame.getParent() != null) {
            return;
        }

        try {
            mWindowManager.addView(rootFrame, vmParams);
        } catch (IllegalStateException ignored) {
        } catch (WindowManager.BadTokenException ignored) {
        }
    }

    private View.OnTouchListener onTouchListener() {
        return new View.OnTouchListener() {
            final View collapsedView = mCollapsed;
            final View expandedView = mExpanded;
            private float initialTouchX, initialTouchY;
            private int initialX, initialY;

            public boolean onTouch(View view, MotionEvent motionEvent) {
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = vmParams.x;
                        initialY = vmParams.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        int rawX = (int) (motionEvent.getRawX() - initialTouchX);
                        int rawY = (int) (motionEvent.getRawY() - initialTouchY);
                        mExpanded.setAlpha(1f);
                        mCollapsed.setAlpha(1f);
                        clampMenuPositionOnScreen();
                        //The check for Xdiff <10 && YDiff< 10 because sometime elements moves a little while clicking.
                        //So that is click event.
                        if (Math.abs(rawX) < 10 && Math.abs(rawY) < 10 && isViewCollapsed()) {
                            //When user clicks on the image view of the collapsed layout,
                            //visibility of the collapsed layout will be changed to "View.GONE"
                            //and expanded view will become visible.
                            try {
                                showExpandedMenu();
                            } catch (NullPointerException ignored) {
                            }
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mExpanded.setAlpha(0.5f);
                        mCollapsed.setAlpha(0.5f);
                        //Calculate the X and Y coordinates of the view.
                        vmParams.x = initialX + ((int) (motionEvent.getRawX() - initialTouchX));
                        vmParams.y = initialY + ((int) (motionEvent.getRawY() - initialTouchY));
                        //Update the layout with new X & Y coordinate
                        try {
                            mWindowManager.updateViewLayout(rootFrame, vmParams);
                        } catch (IllegalArgumentException ignored) {
                            // The service health check will rebuild a detached overlay root.
                        } catch (WindowManager.BadTokenException ignored) {
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void refreshLocalizedUi() {
        Preferences.suppressNativeSync = true;
        try {
            if (settingsButton != null) {
                settingsButton.setText(OfflineTranslator.tr("SETTINGS"));
                settingsButton.setContentDescription(OfflineTranslator.tr("Open menu settings"));
            }
            if (hideButton != null)
                hideButton.setText(OfflineTranslator.tr("Hide"));
            if (closeButton != null)
                closeButton.setText(OfflineTranslator.tr("Minimize"));

            activeCollapse = null;
            activeCollapseHeader = null;
            activeCollapseTitle = null;
            mCollapse = null;
            mConnectedGroup = null;
            mCollapseStack.clear();
            if (mSettings != null) {
                mSettings.removeAllViews();
                featureList(SettingsList(), mSettings);
            }
            mCollapse = null;
            mConnectedGroup = null;
            mCollapseStack.clear();
            if (mods != null) {
                mods.removeAllViews();
                featureList(GetFeatureList(), mods);
            }
            if (scrollView != null) scrollView.scrollTo(0, 0);
            Log.i(TAG, "Offline menu language=" + OfflineTranslator.getPreferredLanguage());
        } finally {
            Preferences.suppressNativeSync = false;
        }
    }

    private void featureList(String[] listFT, LinearLayout linearLayout) {
        int featNum, subFeat = 0;
        LinearLayout llBak = linearLayout;
        if (linearLayout == mods) {
            stopCompatibilityStatusRefresh();
            compatibilityStatusView = null;
            compatibilityStatusText = null;
        }

        for (int i = 0; i < listFT.length; i++) {
            boolean switchedOn = false;
            //Log.i("featureList", listFT[i]);
            String feature = OfflineTranslator.translateFeatureDescriptor(listFT[i]);
            if (feature.endsWith("_True_ForTesting")) {
                switchedOn = true;
                feature = feature.substring(0, feature.length() - "_True_ForTesting".length()) + "_ForTesting";
            } else if (feature.endsWith("_True")) {
                switchedOn = true;
                feature = feature.substring(0, feature.length() - "_True".length());
            }
            boolean forTesting = feature.endsWith("_ForTesting");
            if (forTesting) {
                feature = feature.substring(0, feature.length() - "_ForTesting".length());
            }

            // Resolve explicit IDs before stripping CollapseAdd_ so signed child IDs remain stable.
            String[] idSplit = feature.split("_", 2);
            if (idSplit.length == 2 && (TextUtils.isDigitsOnly(idSplit[0]) || idSplit[0].matches("-[0-9]+"))) {
                featNum = Integer.parseInt(idSplit[0]);
                feature = idSplit[1];
                subFeat++;
            } else {
                featNum = i - subFeat;
            }

            linearLayout = llBak;
            boolean collapseChild = feature.startsWith("CollapseAdd_");
            if (collapseChild) {
                linearLayout = mCollapse;
                feature = feature.substring("CollapseAdd_".length());
                if (mConnectedGroup != null && !feature.startsWith("Group_") && !feature.equals("GroupEnd")) {
                    linearLayout = mConnectedGroup;
                }
            }
            if (linearLayout == null) {
                Log.e(TAG, "Skipping feature without a valid parent: " + feature);
                continue;
            }
            String[] strSplit = feature.split("_");
            int firstAddedView = linearLayout.getChildCount();
            switch (strSplit[0]) {
                case "Toggle":
                    Switch(linearLayout, featNum, strSplit[1], switchedOn);
                    break;
                case "SeekBar":
                    SeekBar(linearLayout, featNum, strSplit[1], Integer.parseInt(strSplit[2]), Integer.parseInt(strSplit[3]));
                    break;
                case "Button":
                    Button(linearLayout, featNum, strSplit[1]);
                    break;
                case "ActionButton":
                    ActionButton(linearLayout, featNum, strSplit[1]);
                    break;
                case "ButtonOnOff":
                    ButtonOnOff(linearLayout, featNum, strSplit[1], switchedOn);
                    break;
                case "Spinner":
                    TextView(linearLayout, strSplit[1]);
                    Spinner(linearLayout, featNum, strSplit[1], strSplit[2]);
                    break;
                case "MultiSelectSpinner":
                    MultiSelectSpinner(linearLayout, featNum, strSplit[1], strSplit[2]);
                    break;
                case "InputText":
                    if (strSplit.length == 3)
                        InputText(linearLayout, featNum, strSplit[2], strSplit[1]);
                    if (strSplit.length == 2)
                        InputText(linearLayout, featNum, strSplit[1]);
                    break;
                case "InputValue":
                    if (strSplit.length == 3)
                        InputNum(linearLayout, featNum, strSplit[2], Integer.parseInt(strSplit[1]));
                    if (strSplit.length == 2)
                        InputNum(linearLayout, featNum, strSplit[1], 0);
                    break;
                case "InputFloat":
                    if (strSplit.length == 3)
                        InputFloat(linearLayout, featNum, strSplit[2], Double.parseDouble(strSplit[1]));
                    if (strSplit.length == 2)
                        InputFloat(linearLayout, featNum, strSplit[1], 0);
                    break;
                case "InputLValue":
                    if (strSplit.length == 3)
                        InputLNum(linearLayout, featNum, strSplit[2], Long.parseLong(strSplit[1]));
                    if (strSplit.length == 2)
                        InputLNum(linearLayout, featNum, strSplit[1], 0);
                    break;
                case "CheckBox":
                    CheckBox(linearLayout, featNum, strSplit[1], switchedOn);
                    break;
                case "RadioButton":
                    RadioButton(linearLayout, featNum, strSplit[1], strSplit[2]);
                    break;
                case "Collapse":
                    Collapse(linearLayout, strSplit[1], switchedOn);
                    subFeat++;
                    break;
                case "Group":
                    mConnectedGroup = ConnectedGroup(linearLayout, strSplit[1]);
                    subFeat++;
                    break;
                case "GroupEnd":
                    mConnectedGroup = null;
                    subFeat++;
                    break;
                case "CollapseEnd":
                    if (!mCollapseStack.empty()) {
                        mCollapse = mCollapseStack.pop();
                    }
                    subFeat++;
                    break;
                case "ButtonLink":
                    subFeat++;
                    ButtonLink(linearLayout, strSplit[1], strSplit[2]);
                    break;
                case "Category":
                    subFeat++;
                    Category(linearLayout, strSplit[1]);
                    break;
                case "RichTextView":
                    subFeat++;
                    String richText = feature.substring("RichTextView_".length());
                    if (isCompatibilityStatusText(richText)) {
                        TextView statusTextView = CompatibilityStatus(linearLayout, richText);
                        trackCompatibilityStatus(statusTextView, richText);
                    } else {
                        TextView(linearLayout, richText);
                    }
                    break;
                case "RichWebView":
                    subFeat++;
                    WebTextView(linearLayout, strSplit[1]);
                    break;
            }
            if (forTesting) {
                for (int child = firstAddedView; child < linearLayout.getChildCount(); child++) {
                    styleTestingFeature(linearLayout.getChildAt(child));
                }
            }
        }
    }

    private TextView CompatibilityStatus(LinearLayout linLayout, String text) {
        LinearLayout card = new LinearLayout(getContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutParams(featureLayoutParams(4, 8));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBackground(MENU_FEATURE_BG_COLOR, 8, PANEL_BORDER_COLOR, 1));

        TextView label = new TextView(getContext);
        label.setText(OfflineTranslator.tr("SYSTEM STATUS"));
        label.setTextColor(ACCENT_COLOR);
        label.setTextSize(11f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        card.addView(label, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView status = new TextView(getContext);
        status.setTextColor(TEXT_COLOR);
        status.setTextSize(14f);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(0, dp(7), 0, 0);
        setCompatibilityStatusText(status, text);
        card.addView(status, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        TextView note = new TextView(getContext);
        note.setText(OfflineTranslator.tr("Checked automatically when the game library is available."));
        note.setTextColor(TEXT_MUTED);
        note.setTextSize(11f);
        note.setPadding(0, dp(5), 0, 0);
        card.addView(note, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        linLayout.addView(card);
        return status;
    }

    private void setCompatibilityStatusText(TextView textView, String text) {
        String compactStatus = enrichCompatibilityStatus(compactCompatibilityStatus(text));
        textView.setText(Html.fromHtml("<font color='" + compatibilityStatusColor(text) + "'>" +
                TextUtils.htmlEncode(compactStatus) + "</font>"));
        textView.setContentDescription("System status: " + compactStatus);
    }

    private String enrichCompatibilityStatus(String status) {
        String cleanStatus = status == null || status.length() == 0
                ? OfflineTranslator.tr("Checking game library") : status;
        if (hasGameIdentity(cleanStatus)) {
            return cleanStatus;
        }

        String identity = detectedGameIdentity();
        return identity.length() == 0 ? cleanStatus : cleanStatus + " · " + identity;
    }

    private boolean hasGameIdentity(String status) {
        String lower = status == null ? "" : status.toLowerCase();
        return lower.contains("game:") && (lower.contains("version") || lower.contains("build"));
    }

    private String detectedGameIdentity() {
        if (detectedGameIdentityCache != null) {
            return detectedGameIdentityCache;
        }

        Context context = getContext;
        if (context == null) {
            detectedGameIdentityCache = "";
            return detectedGameIdentityCache;
        }

        try {
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            CharSequence label = applicationInfo == null ? null
                    : packageManager.getApplicationLabel(applicationInfo);
            String title = label == null ? "" : label.toString().trim();
            if (title.length() == 0) {
                title = packageName;
            }

            StringBuilder identity = new StringBuilder("Game: ");
            identity.append(title);
            if (packageInfo.versionName != null && packageInfo.versionName.trim().length() > 0) {
                identity.append(" · Version ").append(packageInfo.versionName.trim());
            }

            long build = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
            if (build > 0) {
                identity.append(" · Build ").append(build);
            }

            detectedGameIdentityCache = identity.toString();
        } catch (Throwable ignored) {
            detectedGameIdentityCache = "";
        }
        return detectedGameIdentityCache;
    }

    private void applyDetectedGameHeader(TextView titleView) {
        Context context = getContext;
        if (context == null || titleView == null) {
            return;
        }

        try {
            PackageManager packageManager = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            CharSequence label = applicationInfo == null ? null
                    : packageManager.getApplicationLabel(applicationInfo);
            String gameTitle = label == null ? "" : label.toString().trim();
            if (gameTitle.length() == 0) {
                gameTitle = packageName;
            }
            String gameVersion = packageInfo.versionName == null
                    ? "" : packageInfo.versionName.trim();

            StringBuilder richTitle = new StringBuilder("<b><font color='#F4F7FA'>");
            richTitle.append(TextUtils.htmlEncode(gameTitle.toUpperCase(Locale.ROOT)));
            richTitle.append("</font>");
            if (gameVersion.length() > 0) {
                richTitle.append(" <font color='#E8B86A'>");
                richTitle.append(TextUtils.htmlEncode(gameVersion));
                richTitle.append("</font>");
            }
            richTitle.append("</b>");

            titleView.setText(Html.fromHtml(richTitle.toString()));
            titleView.setContentDescription(gameVersion.length() == 0
                    ? gameTitle : gameTitle + " " + gameVersion);
        } catch (Throwable ignored) {
            // Preserve the module's native fallback title when package metadata is unavailable.
        }
    }

    private boolean isCompatibilityStatusText(String text) {
        return text != null && (text.contains("<b>Compatibility:</b>")
                || text.contains("<b>Compatbility:</b>")
                || text.contains("<b>Menu status:</b>")
                || text.contains("<b>System status:</b>"));
    }

    private String compactCompatibilityStatus(String text) {
        if (text == null) {
            return OfflineTranslator.tr("Checking game library");
        }

        String status = text
                .replace("<b>Compatibility:</b>", "")
                .replace("<b>Compatbility:</b>", "")
                .replace("<b>Menu status:</b>", "")
                .replace("<b>System status:</b>", "")
                .replace("</font>", "")
                .replace("<br>", " ")
                .replace("<br/>", " ")
                .replace("<br />", " ")
                .replace("|", "·");
        status = status.replaceAll("<[^>]+>", "").trim();
        return status.length() == 0 ? OfflineTranslator.tr("Checking game library") : status;
    }

    private String compatibilityStatusColor(String text) {
        String status = compactCompatibilityStatus(text).toLowerCase();
        if (status.contains("ready")) {
            return "#69D28C";
        }
        if (status.contains("waiting") || status.contains("installing") || status.contains("checking")) {
            return "#E8B86A";
        }
        return "#FF7A7A";
    }

    private String normalizeFeatureDescriptor(String feature) {
        if (feature == null) {
            return null;
        }
        String translated = OfflineTranslator.translateFeatureDescriptor(feature);
        String[] idSplit = translated.split("_", 2);
        if (idSplit.length == 2 && (TextUtils.isDigitsOnly(idSplit[0]) || idSplit[0].matches("-[0-9]+"))) {
            return idSplit[1];
        }
        return translated;
    }

    private void trackCompatibilityStatus(final TextView textView, String text) {
        if (!isCompatibilityStatusText(text)) {
            return;
        }
        compatibilityStatusView = textView;
        compatibilityStatusText = text;
        scheduleCompatibilityStatusRefresh();
    }

    private void scheduleCompatibilityStatusRefresh() {
        stopCompatibilityStatusRefresh();
        compatibilityStatusRefresh = new Runnable() {
            @Override
            public void run() {
                if (compatibilityStatusView == null || compatibilityStatusView.getParent() == null) {
                    stopCompatibilityStatusRefresh();
                    return;
                }

                String latest = getCurrentCompatibilityStatusText();
                if (latest != null && !latest.equals(compatibilityStatusText)) {
                    compatibilityStatusText = latest;
                    setCompatibilityStatusText(compatibilityStatusView, latest);
                }

                if (!IsGameLibLoaded() && !stopChecking) {
                    compatibilityStatusHandler.postDelayed(this, 1000);
                } else {
                    stopCompatibilityStatusRefresh();
                }
            }
        };
        compatibilityStatusHandler.postDelayed(compatibilityStatusRefresh, 1000);
    }

    private void stopCompatibilityStatusRefresh() {
        if (compatibilityStatusRefresh != null) {
            compatibilityStatusHandler.removeCallbacks(compatibilityStatusRefresh);
            compatibilityStatusRefresh = null;
        }
    }

    private String getCurrentCompatibilityStatusText() {
        String[] features = GetFeatureList();
        if (features == null || features.length == 0) {
            return null;
        }

        for (String rawFeature : features) {
            String feature = normalizeFeatureDescriptor(rawFeature);
            if (feature == null) {
                continue;
            }

            if (feature.startsWith("CollapseAdd_")) {
                feature = feature.substring("CollapseAdd_".length());
            }

            String prefix = "RichTextView_";
            if (!feature.startsWith(prefix)) {
                continue;
            }

            String text = feature.substring(prefix.length());
            if (isCompatibilityStatusText(text)) {
                return text;
            }
        }

        return null;
    }
    private void styleTestingFeature(View view) {
        view.setBackground(roundedBackground(TESTING_BG_COLOR, 6, TESTING_BORDER_COLOR, 1));
        TextView label = findTestingLabel(view);
        if (label == null) {
            return;
        }

        String originalText = label.getText() == null ? "" : label.getText().toString();
        label.setText(Html.fromHtml("<font color='#65C7D0'><small><b>" + OfflineTranslator.tr("FOR TESTING") + "</b></small></font>&nbsp;&nbsp;" +
                TextUtils.htmlEncode(originalText)));
        label.setTextColor(TEXT_COLOR);
        label.setContentDescription("For testing: " + originalText);
    }

    private TextView findTestingLabel(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView label = findTestingLabel(group.getChildAt(i));
            if (label != null) {
                return label;
            }
        }
        return null;
    }

    private void setCollapseHeaderText(TextView header, String marker, String title) {
        String label = marker + "   " + title;
        CharSequence description = header.getContentDescription();
        boolean forTesting = description != null && description.toString().startsWith("For testing:");

        if (forTesting) {
            header.setText(Html.fromHtml("<font color='#65C7D0'><small><b>" +
                    OfflineTranslator.tr("FOR TESTING") + "</b></small></font>&nbsp;&nbsp;" +
                    TextUtils.htmlEncode(label)));
            header.setContentDescription("For testing: " + label);
            return;
        }

        header.setText(label);
    }

    private void showOneShotToast(CharSequence message) {
        showOneShotToast(message, Toast.LENGTH_SHORT);
    }

    private void showOneShotToast(CharSequence message, int length) {
        if (message == null) return;
        String text = message.toString().trim();
        if (text.length() == 0) return;
        Main.ShowNativeToast(getContext, text, length);
    }

    private void showToggleToast(CharSequence label, boolean enabled) {
        if (label == null) return;
        showOneShotToast(label.toString() + ": " + (enabled ? "ON" : "OFF"));
    }

    private void Switch(LinearLayout linLayout, final int featNum, final String featName, boolean swiOn) {
        final LinearLayout row = new LinearLayout(getContext);
        row.setLayoutParams(featureLayoutParams(3, 3));
        row.setMinimumHeight(dp(50));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(10), 0);
        row.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));

        TextView label = new TextView(getContext);
        label.setText(featName);
        label.setTextColor(TEXT_COLOR_2);
        label.setTextSize(14f);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, MATCH_PARENT, 1f));

        final TemplateSwitch switchR = new TemplateSwitch(getContext);
        boolean loadedState;
        if (featNum == -700) {
            loadedState = Preferences.with(getContext).readBoolean("menu_animations", false);
        } else if (featNum == -701) {
            loadedState = Preferences.with(getContext).readBoolean("menu_color_animations", false);
        } else {
            loadedState = Preferences.loadPrefBool(featName, featNum, swiOn);
        }
        switchR.setChecked(loadedState);
        if (featNum == -700) {
            menuAnimations = loadedState;
        } else if (featNum == -701) {
            colorAnimations = loadedState;
        }
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                boolean bool = !switchR.isChecked();
                switchR.setChecked(bool, true);
                if (featNum == -700) {
                    Preferences.with(getContext).writeBoolean("menu_animations", bool);
                    menuAnimations = bool;
                } else if (featNum == -701) {
                    Preferences.with(getContext).writeBoolean("menu_color_animations", bool);
                    colorAnimations = bool;
                    updateColorAnimationState();
                } else {
                    if (featNum >= 0) {
                        showToggleToast(label.getText(), bool);
                    }
                    Preferences.changeFeatureBool(featName, featNum, bool);
                }
                animateToggleFeedback(switchR);
                switch (featNum) {
                    case -1: //Save perferences
                        Preferences.with(getContext).writeBoolean(-1, bool);
                        if (!bool) Preferences.with(getContext).clear(); //Clear perferences if switched off
                        break;
                    case -3:
                        Preferences.isExpanded = bool;
                        applyMenuSize(bool);
                        clampMenuPositionOnScreen();
                        break;
                }
            }
        });
        attachPressAnimation(row);
        row.addView(label);
        row.addView(switchR);
        linLayout.addView(row);
    }

    private void SeekBar(LinearLayout linLayout, final int featNum, final String featName, final int min, int max) {
        int loadedProg = Preferences.loadPrefInt(featName, featNum);
        LinearLayout linearLayout = new LinearLayout(getContext);
        linearLayout.setLayoutParams(featureLayoutParams(3, 3));
        linearLayout.setPadding(dp(14), dp(10), dp(10), dp(6));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));

        final TextView textView = new TextView(getContext);
        textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + ((loadedProg == 0) ? min : loadedProg)));
        textView.setTextColor(TEXT_COLOR_2);
        textView.setTextSize(13f);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        final int[] currentProgress = {((loadedProg == 0) ? min : loadedProg)};

        TemplateSeekBar seekBar = new TemplateSeekBar(getContext);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, dp(30)));
        seekBar.configure(min, max, currentProgress[0], new TemplateSeekListener() {
            public void onValueChanged(int value, boolean fromUser) {
                currentProgress[0] = value;
                textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + value));
                if (fromUser) {
                    Preferences.changeFeatureInt(featName, featNum, value);
                }
            }
        });
        linearLayout.addView(textView);
        linearLayout.addView(seekBar);

        linLayout.addView(linearLayout);
    }

    private void Button(LinearLayout linLayout, final int featNum, final String featName) {
        Button(linLayout, featNum, featName, BTN_COLOR);
    }

    private void ActionButton(LinearLayout linLayout, final int featNum, final String featName) {
        Button(linLayout, featNum, featName, Color.parseColor("#FF72562F"));
    }

    private void Button(LinearLayout linLayout, final int featNum, final String featName, int backgroundColor) {
        final Button button = new Button(getContext);
        button.setLayoutParams(featureLayoutParams(4, 4));
        styleCommandButton(button, backgroundColor);
        button.setText(Html.fromHtml(featName));
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (featNum) {

                    case -6:
                        showMenuContent(mods);
                        settingsOpen = false;
                        break;
                    case -100:
                        stopChecking = true;
                        break;
                }
                if (featNum >= 0) {
                    showOneShotToast(button.getText());
                }
                Preferences.changeFeatureInt(featName, featNum, 0);
            }
        });

        linLayout.addView(button);
    }

    private void ButtonLink(LinearLayout linLayout, final String featName, final String url) {
        final Button button = new Button(getContext);
        button.setLayoutParams(featureLayoutParams(4, 4));
        styleCommandButton(button, BTN_COLOR);
        button.setText(Html.fromHtml(featName));
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setData(Uri.parse(url));
                getContext.startActivity(intent);
            }
        });
        linLayout.addView(button);
    }

    private void ButtonOnOff(LinearLayout linLayout, final int featNum, String featName, boolean switchedOn) {
        final Button button = new Button(getContext);
        button.setLayoutParams(featureLayoutParams(4, 4));
        styleCommandButton(button, BtnOFF);

        final String finalfeatName = featName.replace("OnOff_", "");
        boolean isOn = Preferences.loadPrefBool(featName, featNum, switchedOn);
        if (isOn) {
            button.setText(Html.fromHtml(finalfeatName + ": ON"));
            button.setBackground(roundedBackground(BtnON, 6, PANEL_BORDER_COLOR, 1));
            isOn = false;
        } else {
            button.setText(Html.fromHtml(finalfeatName + ": OFF"));
            button.setBackground(roundedBackground(BtnOFF, 6, PANEL_BORDER_COLOR, 1));
            isOn = true;
        }
        final boolean finalIsOn = isOn;
        button.setOnClickListener(new View.OnClickListener() {
            boolean isOn = finalIsOn;

            public void onClick(View v) {
                showToggleToast(finalfeatName, isOn);
                Preferences.changeFeatureBool(finalfeatName, featNum, isOn);
                //Log.d(TAG, finalfeatName + " " + featNum + " " + isActive2);
                if (isOn) {
                    button.setText(Html.fromHtml(finalfeatName + ": ON"));
                    button.setBackground(roundedBackground(BtnON, 6, PANEL_BORDER_COLOR, 1));
                    isOn = false;
                } else {
                    button.setText(Html.fromHtml(finalfeatName + ": OFF"));
                    button.setBackground(roundedBackground(BtnOFF, 6, PANEL_BORDER_COLOR, 1));
                    isOn = true;
                }
            }
        });
        linLayout.addView(button);
    }

    private void Spinner(LinearLayout linLayout, final int featNum, final String featName, final String list) {
        Log.d(TAG, "spinner " + featNum + " " + featName + " " + list);
        final List<String> lists = new LinkedList<>(Arrays.asList(list.split(",")));

        // Create another LinearLayout as a workaround to use it as a background
        // to keep the down arrow symbol. No arrow symbol if setBackgroundColor set
        LinearLayout linearLayout2 = new LinearLayout(getContext);
        LinearLayout.LayoutParams layoutParams2 = featureLayoutParams(3, 3);
        linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout2.setGravity(Gravity.CENTER_VERTICAL);
        linearLayout2.setMinimumHeight(dp(48));
        linearLayout2.setPadding(dp(8), 0, dp(8), 0);
        linearLayout2.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));
        linearLayout2.setLayoutParams(layoutParams2);

        final TextView selected = new TextView(getContext);
        int initial = featNum == -702
                ? OfflineTranslator.getPreferredLanguage()
                : Preferences.loadPrefInt(featName, featNum);
        initial = Math.max(0, Math.min(lists.size() - 1, initial));
        selected.setText(lists.get(initial));
        styleSpinnerItem(selected, false);
        selected.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));
        TextView arrow = new TextView(getContext);
        arrow.setText("▼");
        arrow.setTextColor(TEXT_COLOR);
        arrow.setTextSize(10f);
        arrow.setGravity(Gravity.CENTER);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(48)));
        linearLayout2.addView(selected);
        linearLayout2.addView(arrow);
        attachPressAnimation(linearLayout2);
        linearLayout2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View anchor) {
                LinearLayout options = new LinearLayout(getContext);
                options.setOrientation(LinearLayout.VERTICAL);
                ScrollView optionScroller = new ScrollView(getContext);
                optionScroller.setFillViewport(false);
                optionScroller.setVerticalScrollBarEnabled(true);
                optionScroller.setScrollbarFadingEnabled(false);
                optionScroller.setBackground(roundedBackground(Color.parseColor("#FF202830"), 6, PANEL_BORDER_COLOR, 1));
                optionScroller.addView(options, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                // A focusable application popup makes some games receive window-focus loss and
                // open their own pause screen. Menu options only need touch input, so preserve the
                // game window's focus while the list is visible.
                int popupGap = dp(4);
                int[] anchorLocation = new int[2];
                anchor.getLocationOnScreen(anchorLocation);
                android.graphics.Rect visibleFrame = new android.graphics.Rect();
                anchor.getWindowVisibleDisplayFrame(visibleFrame);
                int spaceBelow = Math.max(0,
                        visibleFrame.bottom - (anchorLocation[1] + anchor.getHeight()) - popupGap);
                int spaceAbove = Math.max(0,
                        anchorLocation[1] - visibleFrame.top - popupGap);
                boolean showAbove = spaceAbove > spaceBelow;
                int availableHeight = Math.max(spaceAbove, spaceBelow);
                int desiredHeight = dp(48) * lists.size();
                int popupHeight = Math.min(desiredHeight, availableHeight);
                final android.widget.PopupWindow popup = new android.widget.PopupWindow(
                        optionScroller, anchor.getWidth(), popupHeight, false);
                popup.setBackgroundDrawable(roundedBackground(Color.parseColor("#FF202830"), 6, PANEL_BORDER_COLOR, 1));
                popup.setOutsideTouchable(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) popup.setElevation(dp(10));
                for (int i = 0; i < lists.size(); i++) {
                    final int position = i;
                    final String option = lists.get(i);
                    TextView item = new TextView(getContext);
                    item.setText(option);
                    styleSpinnerItem(item, true);
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            selected.setText(option);
                            if (featNum == -702) {
                                OfflineTranslator.setPreferredLanguage(getContext, position);
                                refreshLocalizedUi();
                            } else {
                                Preferences.changeFeatureInt(option, featNum, position);
                            }
                            popup.dismiss();
                        }
                    });
                    options.addView(item, new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
                }
                int verticalOffset = showAbove
                        ? -(anchor.getHeight() + popupHeight + popupGap)
                        : popupGap;
                popup.showAsDropDown(anchor, 0, verticalOffset);
            }
        });
        linLayout.addView(linearLayout2);
    }

    // MultiSelectSpinner uses the same inline, non-focus-stealing popup as Spinner. The first
    // CSV entry is the "select all" row; each later entry maps to one bit in the callback value.
    // Zero means the default/all-selected state. Explicit subsets (including none selected) set
    // MULTI_SELECT_EXPLICIT_MARKER so native code can distinguish them from that default.
    private static final int MULTI_SELECT_EXPLICIT_MARKER = 1 << 30;

    private void MultiSelectSpinner(LinearLayout linLayout, final int featNum,
                                    final String featName, final String list) {
        final List<String> entries = new LinkedList<>(Arrays.asList(list.split(",")));
        if (entries.size() < 2) return;

        final LinearLayout spinner = new LinearLayout(getContext);
        spinner.setOrientation(LinearLayout.HORIZONTAL);
        spinner.setGravity(Gravity.CENTER_VERTICAL);
        spinner.setMinimumHeight(dp(48));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));

        final TextView selected = new TextView(getContext);
        selected.setText(multiSelectSpinnerLabel(
                featName, entries, Preferences.loadPrefInt(featName, featNum)));
        styleSpinnerItem(selected, false);
        selected.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView arrow = new TextView(getContext);
        arrow.setText("\u25BC");
        arrow.setTextColor(TEXT_COLOR);
        arrow.setTextSize(10f);
        arrow.setGravity(Gravity.CENTER);
        arrow.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(48)));
        spinner.addView(selected);
        spinner.addView(arrow);
        attachPressAnimation(spinner);
        spinner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View anchor) {
                final LinearLayout options = new LinearLayout(getContext);
                options.setOrientation(LinearLayout.VERTICAL);
                ScrollView optionScroller = new ScrollView(getContext);
                optionScroller.setFillViewport(false);
                optionScroller.setVerticalScrollBarEnabled(true);
                optionScroller.setScrollbarFadingEnabled(false);
                optionScroller.setBackground(roundedBackground(
                        Color.parseColor("#FF202830"), 6, PANEL_BORDER_COLOR, 1));
                optionScroller.addView(options,
                        new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

                int popupGap = dp(4);
                int[] anchorLocation = new int[2];
                anchor.getLocationOnScreen(anchorLocation);
                android.graphics.Rect visibleFrame = new android.graphics.Rect();
                anchor.getWindowVisibleDisplayFrame(visibleFrame);
                int spaceBelow = Math.max(0,
                        visibleFrame.bottom - (anchorLocation[1] + anchor.getHeight()) - popupGap);
                int spaceAbove = Math.max(0,
                        anchorLocation[1] - visibleFrame.top - popupGap);
                boolean showAbove = spaceAbove > spaceBelow;
                int availableHeight = Math.max(spaceAbove, spaceBelow);
                int desiredHeight = dp(48) * entries.size();
                int popupHeight = Math.min(desiredHeight, availableHeight);
                final android.widget.PopupWindow popup = new android.widget.PopupWindow(
                        optionScroller, anchor.getWidth(), popupHeight, false);
                popup.setBackgroundDrawable(roundedBackground(
                        Color.parseColor("#FF202830"), 6, PANEL_BORDER_COLOR, 1));
                popup.setOutsideTouchable(true);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    popup.setElevation(dp(10));
                }

                final RadioButton[] choices = new RadioButton[entries.size()];
                final int allMask = multiSelectAllMask(entries);
                int current = Preferences.loadPrefInt(featName, featNum);
                int currentMask = current == 0 ? allMask : current & allMask;
                for (int index = 0; index < entries.size(); index++) {
                    final int position = index;
                    LinearLayout optionRow = new LinearLayout(getContext);
                    optionRow.setOrientation(LinearLayout.HORIZONTAL);
                    optionRow.setGravity(Gravity.CENTER_VERTICAL);
                    optionRow.setPadding(dp(6), 0, dp(8), 0);

                    RadioButton choice = new RadioButton(getContext);
                    choice.setText(entries.get(index));
                    choice.setTextColor(TEXT_COLOR_2);
                    choice.setTextSize(14f);
                    choice.setGravity(Gravity.CENTER_VERTICAL);
                    choice.setClickable(false);
                    choice.setFocusable(false);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        choice.setButtonTintList(ColorStateList.valueOf(RadioColor));
                    }
                    choice.setChecked(index == 0
                            ? currentMask == allMask
                            : (currentMask & multiSelectBit(index)) != 0);
                    choices[index] = choice;
                    optionRow.addView(choice,
                            new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
                    attachPressAnimation(optionRow);
                    optionRow.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (position == 0) {
                                boolean selectAll = !choices[0].isChecked();
                                for (int item = 1; item < choices.length; item++) {
                                    choices[item].setChecked(selectAll);
                                }
                            } else {
                                choices[position].setChecked(!choices[position].isChecked());
                            }

                            int mask = 0;
                            for (int item = 1; item < choices.length; item++) {
                                if (choices[item].isChecked()) {
                                    mask |= multiSelectBit(item);
                                }
                            }
                            choices[0].setChecked(mask == allMask);
                            int encoded = mask == allMask
                                    ? 0
                                    : MULTI_SELECT_EXPLICIT_MARKER | mask;
                            Preferences.changeFeatureInt(featName, featNum, encoded);
                            selected.setText(multiSelectSpinnerLabel(featName, entries, encoded));
                            showOneShotToast(multiSelectSelectionToast(entries, encoded));
                        }
                    });
                    options.addView(optionRow,
                            new LinearLayout.LayoutParams(MATCH_PARENT, dp(48)));
                }

                int verticalOffset = showAbove
                        ? -(anchor.getHeight() + popupHeight + popupGap)
                        : popupGap;
                popup.showAsDropDown(anchor, 0, verticalOffset);
            }
        });
        linLayout.addView(spinner, featureLayoutParams(3, 3));
    }

    private int multiSelectBit(int index) {
        return index > 0 && index <= 30 ? 1 << (index - 1) : 0;
    }

    private int multiSelectAllMask(List<String> entries) {
        int mask = 0;
        for (int index = 1; index < entries.size(); index++) {
            mask |= multiSelectBit(index);
        }
        return mask;
    }

    private String multiSelectSpinnerLabel(String featName, List<String> entries, int encoded) {
        if (encoded == 0) return featName + ": " + entries.get(0);
        int selectedMask = encoded & multiSelectAllMask(entries);
        StringBuilder selected = new StringBuilder(featName).append(": ");
        boolean hasSelection = false;
        for (int index = 1; index < entries.size(); index++) {
            if ((selectedMask & multiSelectBit(index)) == 0) continue;
            if (hasSelection) selected.append(", ");
            selected.append(entries.get(index));
            hasSelection = true;
        }
        if (!hasSelection) selected.append("none");
        return selected.toString();
    }

    private String multiSelectSelectionToast(List<String> entries, int encoded) {
        if (encoded == 0) return "Selected: " + entries.get(0);
        return "Selected: " + multiSelectSpinnerLabel("", entries, encoded)
                .replaceFirst("^: ", "");
    }

    private EditText createInputEditText(AlertDialog.Builder builder) {
        EditText editText = new EditText(builder.getContext());
        editText.setSingleLine(true);
        editText.setTextColor(Color.parseColor("#FF4E5357"));
        editText.setHintTextColor(Color.parseColor("#FF6D7276"));
        editText.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FF087B46")));
        return editText;
    }

    private void showInputDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        android.view.Window window = Objects.requireNonNull(dialog.getWindow());
        if (overlayRequired) {
            window.setType(Build.VERSION.SDK_INT >= 26 ? 2038 : 2002);
        }
        dialog.show();
        window.setBackgroundDrawable(roundedBackground(
                Color.parseColor("#FFF4F4F4"), 28, Color.TRANSPARENT, 0));
        int actionColor = Color.parseColor("#FF087B46");
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (positive != null) {
            positive.setTextColor(actionColor);
            positive.setAllCaps(false);
        }
        if (negative != null) {
            negative.setTextColor(actionColor);
            negative.setAllCaps(false);
        }
    }

    private void InputNum(LinearLayout linLayout, final int featNum, final String featName, final int maxValue) {
        LinearLayout linearLayout = new LinearLayout(getContext);
        linearLayout.setLayoutParams(featureLayoutParams(4, 4));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));

        final Button button = new Button(getContext);
        int num = Preferences.loadPrefInt(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setLayoutParams(layoutParams);
        styleCommandButton(button, BTN_COLOR);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder alertName = new AlertDialog.Builder(
                        getContext, android.R.style.Theme_Material_Light_Dialog_Alert);
                final EditText editText = createInputEditText(alertName);
                if (maxValue != 0)
                    editText.setHint("Max value: " + maxValue);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                InputFilter[] FilterArray = new InputFilter[1];
                FilterArray[0] = new InputFilter.LengthFilter(10);
                editText.setFilters(FilterArray);
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                        } else {
                            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                        }
                    }
                });
                editText.requestFocus();

                alertName.setTitle("Input number");
                alertName.setView(editText);
                LinearLayout layoutName = new LinearLayout(getContext);
                layoutName.setOrientation(LinearLayout.VERTICAL);
                layoutName.addView(editText); // displays the user input bar
                alertName.setView(layoutName);

                alertName.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        int num;
                        try {
                            String inp = editText.getText().toString();
                            num = Integer.parseInt(inp.isEmpty() ? "0" : inp);
                            if (maxValue != 0 && num >= maxValue)
                                num = maxValue;
                        } catch (NumberFormatException ex) {
                            if (maxValue != 0)
                                num = maxValue;
                            else
                                num = Integer.MAX_VALUE;
                        }

                        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                        Preferences.changeFeatureInt(featName, featNum, num);
                        editText.setFocusable(false);
                    }
                });

                alertName.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // dialog.cancel(); // closes dialog
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                    }
                });

                showInputDialog(alertName);
            }
        });

        linearLayout.addView(button);
        linLayout.addView(linearLayout);
    }

    private void InputLNum(LinearLayout linLayout, final int featNum, final String featName, final long maxValue) {
        LinearLayout linearLayout = new LinearLayout(getContext);
        linearLayout.setLayoutParams(featureLayoutParams(4, 4));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));

        final Button button = new Button(getContext);
        long num = Preferences.loadPrefLong(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
        button.setLayoutParams(layoutParams);
        styleCommandButton(button, BTN_COLOR);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder alertName = new AlertDialog.Builder(
                        getContext, android.R.style.Theme_Material_Light_Dialog_Alert);
                final EditText editText = createInputEditText(alertName);
                if (maxValue != 0)
                    editText.setHint("Max value: " + maxValue);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setKeyListener(DigitsKeyListener.getInstance("0123456789-"));
                InputFilter[] FilterArray = new InputFilter[1];
                FilterArray[0] = new InputFilter.LengthFilter(20);
                editText.setFilters(FilterArray);
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                        } else {
                            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                        }
                    }
                });
                editText.requestFocus();

                alertName.setTitle("Input number");
                alertName.setView(editText);
                LinearLayout layoutName = new LinearLayout(getContext);
                layoutName.setOrientation(LinearLayout.VERTICAL);
                layoutName.addView(editText); // displays the user input bar
                alertName.setView(layoutName);

                alertName.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        long num;
                        try {
                            String inp = editText.getText().toString();
                            num = Long.parseLong(inp.isEmpty() ? "0" : inp);
                            if (maxValue != 0 && num >= maxValue)
                                num = maxValue;
                        } catch (NumberFormatException ex) {
                            if (maxValue != 0)
                                num = maxValue;
                            else
                                num = Long.MAX_VALUE;
                        }

                        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + num + "</font>"));
                        Preferences.changeFeatureLong(featName, featNum, num);

                        editText.setFocusable(false);
                    }
                });

                alertName.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        // dialog.cancel(); // closes dialog
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                    }
                });

                showInputDialog(alertName);
            }
        });

        linearLayout.addView(button);
        linLayout.addView(linearLayout);
    }

    private String formatFloatInput(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            return "";
        String text = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        if (!text.contains("."))
            text += ".0";
        return text;
    }

    private String formatFloatInput(String value) {
        if (value == null)
            return "0.0";
        String text = value.trim().replace(',', '.');
        if (text.isEmpty())
            return "0.0";
        try {
            return formatFloatInput(Double.parseDouble(text));
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    private String formatFloatMaxInput(double value) {
        String text = Double.toString(value);
        if (text.endsWith(".0"))
            text = text.substring(0, text.length() - 2);
        return text;
    }

    private void InputFloat(LinearLayout linLayout, final int featNum, final String featName, final double maxValue) {
        LinearLayout linearLayout = new LinearLayout(getContext);
        linearLayout.setLayoutParams(featureLayoutParams(4, 4));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));

        final Button button = new Button(getContext);
        String string = formatFloatInput(Preferences.loadPrefString(featName, featNum));
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + string + "</font>"));
        button.setLayoutParams(layoutParams);
        styleCommandButton(button, BTN_COLOR);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder alertName = new AlertDialog.Builder(
                        getContext, android.R.style.Theme_Material_Light_Dialog_Alert);
                final EditText editText = createInputEditText(alertName);
                editText.setText(formatFloatInput(Preferences.loadPrefString(featName, featNum)));
                editText.setSelection(editText.getText().length());
                if (maxValue != 0)
                    editText.setHint("Max value: " + formatFloatMaxInput(maxValue));
                editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                editText.setKeyListener(DigitsKeyListener.getInstance(false, true));
                editText.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                InputFilter[] FilterArray = new InputFilter[1];
                FilterArray[0] = new InputFilter.LengthFilter(16);
                editText.setFilters(FilterArray);
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                        } else {
                            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                        }
                    }
                });
                editText.requestFocus();

                alertName.setTitle("Input decimal number");
                LinearLayout layoutName = new LinearLayout(getContext);
                layoutName.setOrientation(LinearLayout.VERTICAL);
                layoutName.addView(editText);
                alertName.setView(layoutName);

                alertName.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String str = editText.getText().toString().trim().replace(',', '.');
                        if (str.isEmpty()) {
                            str = "0.0";
                        } else {
                            try {
                                double num = Double.parseDouble(str);
                                if (num < 0)
                                    num = 0;
                                if (maxValue != 0 && num > maxValue)
                                    num = maxValue;
                                str = formatFloatInput(num);
                            } catch (NumberFormatException ex) {
                                str = "0.0";
                            }
                        }

                        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + str + "</font>"));
                        Preferences.changeFeatureString(featName, featNum, str);
                        editText.setFocusable(false);
                    }
                });

                alertName.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                    }
                });

                showInputDialog(alertName);
            }
        });

        linearLayout.addView(button);
        linLayout.addView(linearLayout);
    }

    private void InputText(LinearLayout linLayout, final int featNum, final String featName) {
        InputText(linLayout, featNum, featName, "");
    }

    private void InputText(LinearLayout linLayout, final int featNum, final String featName, final String defaultText) {
        LinearLayout linearLayout = new LinearLayout(getContext);
        linearLayout.setLayoutParams(featureLayoutParams(4, 4));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(46));

        final Button button = new Button(getContext);

        String string = Preferences.loadPrefString(featName, featNum);
        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + string + "</font>"));

        button.setLayoutParams(layoutParams);
        styleCommandButton(button, BTN_COLOR);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder alertName = new AlertDialog.Builder(
                        getContext, android.R.style.Theme_Material_Light_Dialog_Alert);

                final EditText editText = createInputEditText(alertName);
                if (!defaultText.isEmpty())
                    editText.setHint("Default value: " + defaultText);
                editText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View v, boolean hasFocus) {
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (hasFocus) {
                            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                        } else {
                            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                        }
                    }
                });
                editText.requestFocus();

                alertName.setTitle("Input text");
                alertName.setView(editText);
                LinearLayout layoutName = new LinearLayout(getContext);
                layoutName.setOrientation(LinearLayout.VERTICAL);
                layoutName.addView(editText); // displays the user input bar
                alertName.setView(layoutName);

                alertName.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        String str = editText.getText().toString();
                        button.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + str + "</font>"));
                        Preferences.changeFeatureString(featName, featNum, str);
                        editText.setFocusable(false);
                    }
                });

                alertName.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        //dialog.cancel(); // closes dialog
                        InputMethodManager imm = (InputMethodManager) getContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                    }
                });


                showInputDialog(alertName);
            }
        });

        linearLayout.addView(button);
        linLayout.addView(linearLayout);
    }

    private void CheckBox(LinearLayout linLayout, final int featNum, final String featName, boolean switchedOn) {
        final CheckBox checkBox = new CheckBox(getContext);
        checkBox.setLayoutParams(featureLayoutParams(3, 3));
        checkBox.setMinHeight(dp(48));
        checkBox.setPadding(dp(12), 0, dp(10), 0);
        checkBox.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));
        checkBox.setText(featName);
        checkBox.setTextColor(TEXT_COLOR_2);
        checkBox.setTextSize(14f);
        checkBox.setButtonTintList(ColorStateList.valueOf(CheckBoxColor));
        checkBox.setChecked(Preferences.loadPrefBool(featName, featNum, switchedOn));
        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                showToggleToast(checkBox.getText(), isChecked);
                if (checkBox.isChecked()) {
                    Preferences.changeFeatureBool(featName, featNum, isChecked);
                } else {
                    Preferences.changeFeatureBool(featName, featNum, isChecked);
                }
            }
        });
        linLayout.addView(checkBox);
    }

    @SuppressLint("SetTextI18n")
    private void RadioButton(LinearLayout linLayout, final int featNum, String featName, final String list) {
        //Credit: LoraZalora
        final List<String> lists = new LinkedList<>(Arrays.asList(list.split(",")));

        final TextView textView = new TextView(getContext);
        textView.setText(featName + ":");
        textView.setTextColor(TEXT_COLOR_2);

        final RadioGroup radioGroup = new RadioGroup(getContext);
        radioGroup.setLayoutParams(featureLayoutParams(3, 3));
        radioGroup.setPadding(dp(12), dp(10), dp(12), dp(10));
        radioGroup.setOrientation(LinearLayout.VERTICAL);
        radioGroup.setBackground(roundedBackground(CONTROL_BG_COLOR, 6, DIVIDER_COLOR, 1));
        radioGroup.addView(textView);

        for (int i = 0; i < lists.size(); i++) {
            final RadioButton Radioo = new RadioButton(getContext);
            final String finalfeatName = featName, radioName = lists.get(i);
            View.OnClickListener first_radio_listener = new View.OnClickListener() {
                public void onClick(View v) {
                    textView.setText(Html.fromHtml(finalfeatName + ": <font color='" + NumberTxtColor + "'>" + radioName));
                    Preferences.changeFeatureInt(finalfeatName, featNum, radioGroup.indexOfChild(Radioo));
                }
            };
            System.out.println(lists.get(i));
            Radioo.setText(lists.get(i));
            Radioo.setTextColor(Color.LTGRAY);
            Radioo.setButtonTintList(ColorStateList.valueOf(RadioColor));
            Radioo.setOnClickListener(first_radio_listener);
            radioGroup.addView(Radioo);
        }

        int index = Preferences.loadPrefInt(featName, featNum);
        if (index > 0) { //Preventing it to get an index less than 1. below 1 = null = crash
            textView.setText(Html.fromHtml(featName + ": <font color='" + NumberTxtColor + "'>" + lists.get(index - 1)));
            ((RadioButton) radioGroup.getChildAt(index)).setChecked(true);
        }
        linLayout.addView(radioGroup);
    }

    @SuppressLint("SetTextI18n")
    private LinearLayout ConnectedGroup(LinearLayout parent, String title) {
        LinearLayout group = new LinearLayout(getContext);
        group.setLayoutParams(featureLayoutParams(6, 6));
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(4), dp(8), dp(4), dp(6));
        group.setBackgroundColor(Color.parseColor("#FF1B2228"));

        TextView label = new TextView(getContext);
        label.setText(title.toUpperCase());
        label.setTextColor(ACCENT_COLOR);
        label.setTextSize(11f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(dp(10), 0, dp(10), dp(6));
        group.addView(label);

        View divider = new View(getContext);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(MATCH_PARENT, dp(1));
        dividerParams.setMargins(dp(10), 0, dp(10), dp(3));
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(DIVIDER_COLOR);
        group.addView(divider);

        parent.addView(group);
        return group;
    }

    @SuppressLint("SetTextI18n")
    private void Collapse(LinearLayout linLayout, final String text, final boolean expanded) {
        mConnectedGroup = null;
        final boolean nestedCollapse = linLayout == mCollapse && mCollapse != null;
        LinearLayout.LayoutParams layoutParamsLL = featureLayoutParams(4, 4);

        LinearLayout collapse = new LinearLayout(getContext);
        collapse.setLayoutParams(layoutParamsLL);
        collapse.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout collapseBody = new LinearLayout(getContext);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        bodyParams.setMargins(dp(12), dp(3), 0, 0);
        collapseBody.setLayoutParams(bodyParams);
        collapseBody.setOrientation(LinearLayout.HORIZONTAL);
        collapseBody.setPadding(dp(8), dp(6), dp(6), dp(8));
        collapseBody.setBackground(roundedBackground(Color.parseColor("#FF171D22"), 4, DIVIDER_COLOR, 1));
        collapseBody.setVisibility(View.GONE);

        View accentRail = new View(getContext);
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(3), MATCH_PARENT);
        railParams.setMargins(0, dp(3), dp(8), dp(3));
        accentRail.setLayoutParams(railParams);
        accentRail.setBackground(roundedBackground(ACCENT_COLOR, 2, Color.TRANSPARENT, 0));

        final LinearLayout collapseSub = new LinearLayout(getContext);
        collapseSub.setLayoutParams(new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f));
        collapseSub.setPadding(0, 0, 0, 0);
        collapseSub.setOrientation(LinearLayout.VERTICAL);
        collapseSub.setBackgroundColor(Color.TRANSPARENT);
        collapseBody.addView(accentRail);
        collapseBody.addView(collapseSub);
        if (linLayout == mCollapse && mCollapse != null) {
            mCollapseStack.push(mCollapse);
        } else {
            mCollapseStack.clear();
        }
        mCollapse = collapseSub;

        final TextView textView = new TextView(getContext);
        textView.setBackground(roundedBackground(CollapseColor, 6, PANEL_BORDER_COLOR, 1));
        setCollapseHeaderText(textView, "+", text);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextColor(TEXT_COLOR);
        textView.setTextSize(14f);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setMinHeight(dp(50));
        textView.setPadding(dp(16), 0, dp(14), 0);
        attachPressAnimation(textView);

        if (expanded) {
            collapseBody.setVisibility(View.VISIBLE);
            setCollapseHeaderText(textView, "-", text);
            textView.setTextColor(ACCENT_COLOR);
            if (!nestedCollapse) {
                activeCollapse = collapseBody;
                activeCollapseHeader = textView;
                activeCollapseTitle = text;
            }
        }

        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean shouldOpen = collapseBody.getVisibility() != View.VISIBLE;
                if (shouldOpen) {
                    if (!nestedCollapse && activeCollapse != null && activeCollapse != collapseBody) {
                        hideCollapseBody(activeCollapse);
                        if (activeCollapseHeader != null && activeCollapseTitle != null) {
                            setCollapseHeaderText(activeCollapseHeader, "+", activeCollapseTitle);
                            activeCollapseHeader.setTextColor(TEXT_COLOR);
                        }
                    }
                    showCollapseBody(collapseBody);
                    animateToggleFeedback(textView);
                    setCollapseHeaderText(textView, "-", text);
                    textView.setTextColor(ACCENT_COLOR);
                    if (!nestedCollapse) {
                        activeCollapse = collapseBody;
                        activeCollapseHeader = textView;
                        activeCollapseTitle = text;
                    }
                    return;
                }
                hideCollapseBody(collapseBody);
                animateToggleFeedback(textView);
                setCollapseHeaderText(textView, "+", text);
                textView.setTextColor(TEXT_COLOR);
                if (!nestedCollapse && activeCollapse == collapseBody) {
                    activeCollapse = null;
                    activeCollapseHeader = null;
                    activeCollapseTitle = null;
                }
            }
        });
        collapse.addView(textView);
        collapse.addView(collapseBody);
        linLayout.addView(collapse);
    }

    private void Category(LinearLayout linLayout, String text) {
        TextView textView = new TextView(getContext);
        textView.setLayoutParams(featureLayoutParams(5, 3));
        textView.setBackground(roundedBackground(CategoryBG, 4, DIVIDER_COLOR, 1));
        textView.setText(Html.fromHtml(text));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setTextColor(ACCENT_COLOR);
        textView.setTextSize(12f);
        textView.setTypeface(null, Typeface.BOLD);
        textView.setMinHeight(dp(36));
        textView.setPadding(dp(12), 0, dp(12), 0);
        linLayout.addView(textView);
    }

    private TextView TextView(LinearLayout linLayout, String text) {
        TextView textView = new TextView(getContext);
        textView.setText(Html.fromHtml(text));
        textView.setTextColor(TEXT_MUTED);
        textView.setTextSize(12f);
        textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textView.setPadding(dp(12), dp(8), dp(12), dp(2));
        linLayout.addView(textView);
        return textView;
    }

    private void WebTextView(LinearLayout linLayout, String text) {
        WebView wView = new WebView(getContext);
        wView.loadData(text, "text/html", "utf-8");
        wView.setBackgroundColor(0x00000000); //Transparent
        wView.setPadding(0, 5, 0, 5);
        wView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        linLayout.addView(wView);
    }

    private boolean isViewCollapsed() {
        return rootFrame == null || mCollapsed.getVisibility() == View.VISIBLE;
    }

    //For our image a little converter
    private int convertDipToPixels(int i) {
        return (int) ((((float) i) * getContext.getResources().getDisplayMetrics().density) + 0.5f);
    }

    private int dp(int i) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float) i, getContext.getResources().getDisplayMetrics());
    }

    public void setVisibility(int view) {
        if (rootFrame != null) {
            ensureOverlayAttached();
            try {
                rootFrame.setVisibility(view);
                if (view != View.VISIBLE) Main.SetMenuExpanded(false);
                updateColorAnimationState();
            } catch (RuntimeException ignored) {
                // Launcher will rebuild a stale WindowManager tree on its next health check.
            }
        }
    }

    public boolean isOverlayAttached() {
        return rootFrame != null
                && rootFrame.getParent() != null
                && rootFrame.isAttachedToWindow();
    }

    public void onDestroy() {
        Main.SetMenuExpanded(false);
        stopCompatibilityStatusRefresh();
        stopChecking = true;
        if (menuLoadCheck != null) {
            menuLoadHandler.removeCallbacks(menuLoadCheck);
            menuLoadCheck = null;
        }
        stopMenuColorAnimation();
        if (rootFrame != null && mWindowManager != null && rootFrame.getParent() != null) {
            try {
                mWindowManager.removeView(rootFrame);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
