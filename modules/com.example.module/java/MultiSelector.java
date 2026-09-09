package com.android.support;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Searchable, capped multi-selector styled after the item spawner dialog. */
final class MultiSelector {
    private static final int COLOR_SURFACE = Color.parseColor("#F2151B20");
    private static final int COLOR_PANEL = Color.parseColor("#FF202830");
    private static final int COLOR_HOLDER = Color.parseColor("#FF29343D");
    private static final int COLOR_HOLDER_SELECTED = Color.parseColor("#FF3A3324");
    private static final int COLOR_BORDER = Color.parseColor("#FF3A4651");
    private static final int COLOR_ACCENT = Color.parseColor("#FFE8B86A");
    private static final int COLOR_TEXT = Color.parseColor("#FFF4F7FA");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#FF9EAAB3");
    private static final int COLOR_ACTION = Color.parseColor("#FF72562F");

    interface Callback {
        void onConfirm(String encodedSelection);
    }

    private MultiSelector() { }

    static void show(final Context context, String title, String[] names, final int cap,
                     String encodedSelection, final Callback callback) {
        if (!(context instanceof Activity) || names == null || names.length == 0) return;
        Activity activity = (Activity) context;
        if (activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;

        final List<Trait> traits = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            traits.add(new Trait(index, names[index]));
        }
        final Set<Integer> selected = decode(encodedSelection, names.length, cap);

        final Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        LinearLayout root = vertical(context);
        root.setPadding(dp(context, 18), dp(context, 16), dp(context, 18), dp(context, 14));
        root.setBackground(background(COLOR_SURFACE, dp(context, 18), COLOR_BORDER, dp(context, 1)));

        TextView heading = text(context, title, 21, COLOR_TEXT, true);
        root.addView(heading, matchWrap());

        final EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setHint("Search traits");
        search.setTextSize(14f);
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(COLOR_TEXT_SECONDARY);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        search.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        search.setBackground(background(COLOR_PANEL, dp(context, 10), COLOR_BORDER, dp(context, 1)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 46));
        searchParams.topMargin = dp(context, 12);
        searchParams.bottomMargin = dp(context, 8);
        root.addView(search, searchParams);

        final TraitAdapter adapter = new TraitAdapter(context, traits, selected, cap);
        final ListView list = new ListView(context);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(dp(context, 5));
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(true);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = horizontal(context);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(context, 12);
        root.addView(footer, footerParams);

        Button clear = commandButton(context, "Clear", COLOR_PANEL);
        footer.addView(clear, new LinearLayout.LayoutParams(0, dp(context, 46), 1f));

        Button cancel = commandButton(context, "Cancel", COLOR_PANEL);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, dp(context, 46), 1f);
        cancelParams.leftMargin = dp(context, 8);
        footer.addView(cancel, cancelParams);

        Button confirm = commandButton(context, "Confirm", COLOR_ACTION);
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0, dp(context, 46), 1.35f);
        confirmParams.leftMargin = dp(context, 10);
        footer.addView(confirm, confirmParams);

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                adapter.filter(value == null ? "" : value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            hideKeyboard(view);
            view.clearFocus();
            return true;
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                selected.clear();
                adapter.notifyDataSetChanged();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                dialog.dismiss();
            }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                hideKeyboard(search);
                callback.onConfirm(encode(selected));
                dialog.dismiss();
            }
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window == null) return;
        View gameInput = activity.getCurrentFocus();
        if (gameInput != null) {
            hideKeyboard(gameInput);
            gameInput.clearFocus();
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.72f;
        window.setAttributes(attributes);
        dialog.show();

        int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 48),
                dp(context, 560));
        int height = Math.min(context.getResources().getDisplayMetrics().heightPixels - dp(context, 48),
                dp(context, 680));
        window.setLayout(Math.max(1, width), Math.max(1, height));
        window.setGravity(Gravity.CENTER);
        dialog.setOnDismissListener(ignored -> hideKeyboard(search));
    }

    private static Set<Integer> decode(String encoded, int count, int cap) {
        Set<Integer> selected = new LinkedHashSet<>();
        if (encoded == null || encoded.isEmpty()) return selected;
        for (String value : encoded.split(";")) {
            try {
                int index = Integer.parseInt(value);
                if (index >= 0 && index < count) selected.add(index);
            } catch (NumberFormatException ignored) { }
            if (cap > 0 && selected.size() >= cap) break;
        }
        return selected;
    }

    private static String encode(Set<Integer> selected) {
        StringBuilder value = new StringBuilder();
        for (Integer index : selected) {
            if (value.length() > 0) value.append(';');
            value.append(index);
        }
        return value.toString();
    }

    private static void hideKeyboard(View view) {
        InputMethodManager keyboard = (InputMethodManager) view.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static TextView text(Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static Button commandButton(Context context, String label, int color) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(13f);
        button.setTextColor(COLOR_TEXT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(color, dp(context, 10), COLOR_BORDER, dp(context, 1)));
        return button;
    }

    private static GradientDrawable background(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Trait {
        final int index;
        final String name;
        final String searchable;

        Trait(int index, String name) {
            this.index = index;
            this.name = name;
            this.searchable = name.toLowerCase(Locale.ROOT);
        }
    }

    private static final class TraitAdapter extends BaseAdapter {
        private final Context context;
        private final List<Trait> all;
        private final List<Trait> visible = new ArrayList<>();
        private final Set<Integer> selected;
        private final int cap;

        TraitAdapter(Context context, List<Trait> traits, Set<Integer> selected, int cap) {
            this.context = context;
            this.all = traits;
            this.selected = selected;
            this.cap = cap;
            visible.addAll(traits);
        }

        void filter(String query) {
            String needle = query.trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (Trait trait : all) {
                if (needle.isEmpty() || trait.searchable.contains(needle)) visible.add(trait);
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return visible.size(); }
        @Override public Trait getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return getItem(position).index; }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            final Trait trait = getItem(position);
            final CheckBox box;
            if (recycled instanceof CheckBox) {
                box = (CheckBox) recycled;
            } else {
                box = new CheckBox(context);
                box.setTextSize(14f);
                box.setTextColor(COLOR_TEXT);
                box.setGravity(Gravity.CENTER_VERTICAL);
                box.setPadding(dp(context, 12), 0, dp(context, 12), 0);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    box.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
                }
            }
            box.setText(trait.name);
            box.setChecked(selected.contains(trait.index));
            box.setBackground(background(selected.contains(trait.index)
                    ? COLOR_HOLDER_SELECTED : COLOR_HOLDER, dp(context, 8), COLOR_BORDER, dp(context, 1)));
            box.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (selected.contains(trait.index)) {
                        selected.remove(trait.index);
                    } else if (cap <= 0 || selected.size() < cap) {
                        selected.add(trait.index);
                    } else {
                        Main.ShowNativeToast(context, "Maximum " + cap + " Selected",
                                Toast.LENGTH_SHORT);
                    }
                    notifyDataSetChanged();
                }
            });
            box.setLayoutParams(new ListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 48)));
            return box;
        }
    }
}


