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
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reusable searchable item picker; game modules supply the catalog and add action. */
final class ItemSpawner {
    private static final String CATEGORY_ALL = "All items";

    private static final int COLOR_SURFACE = Color.parseColor("#F2151B20");
    private static final int COLOR_PANEL = Color.parseColor("#FF202830");
    private static final int COLOR_HOLDER = Color.parseColor("#FF29343D");
    private static final int COLOR_HOLDER_SELECTED = Color.parseColor("#FF3A3324");
    private static final int COLOR_BORDER = Color.parseColor("#FF3A4651");
    private static final int COLOR_ACCENT = Color.parseColor("#FFE8B86A");
    private static final int COLOR_TEXT = Color.parseColor("#FFF4F7FA");
    private static final int COLOR_TEXT_SECONDARY = Color.parseColor("#FF9EAAB3");
    private static final int COLOR_ACTION = Color.parseColor("#FF72562F");

    interface Backend {
        Activity activeActivity();

        String[] loadItemCatalog();

        String categoryFor(int type);

        void addItems(Context context, int[] itemIds, int[] amounts);
    }

    private ItemSpawner() {
    }

    static void show(final Context sourceContext, final Backend backend) {
        if (sourceContext == null || backend == null) return;

        Activity activity = backend.activeActivity();
        final Context dialogContext = activity != null ? activity : sourceContext;
        if (dialogContext instanceof Activity) {
            Activity owner = (Activity) dialogContext;
            if (owner.isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && owner.isDestroyed())) {
                return;
            }
        }
        final DisplayMetrics metrics = dialogContext.getResources().getDisplayMetrics();
        final boolean compactLandscape = metrics.widthPixels > metrics.heightPixels;

        final List<Item> items = loadItems(backend);
        if (items.isEmpty()) {
            Main.ShowNativeToast(sourceContext,
                    "Open a world and inventory before using the item spawner", Toast.LENGTH_LONG);
            return;
        }

        final Dialog dialog = new Dialog(
                dialogContext, android.R.style.Theme_Material_NoActionBar);
        final LinearLayout root = vertical(dialogContext);
        root.setPadding(dp(dialogContext, compactLandscape ? 14 : 18),
                dp(dialogContext, compactLandscape ? 10 : 16),
                dp(dialogContext, compactLandscape ? 14 : 18),
                dp(dialogContext, compactLandscape ? 10 : 14));
        root.setBackground(background(COLOR_SURFACE, dp(dialogContext, 18),
                COLOR_BORDER, dp(dialogContext, 1)));

        TextView title = text(dialogContext, "Add Items",
                compactLandscape ? 19 : 21, COLOR_TEXT, true);
        TextView subtitle = text(dialogContext,
                "Choose items to add to your inventory.",
                compactLandscape ? 11 : 12, COLOR_TEXT_SECONDARY, false);

        final EditText search = new EditText(dialogContext);
        search.setSingleLine(true);
        search.setHint(compactLandscape ? "Name, ID, or category" :
                "Search name, ID, or category");
        search.setTextSize(14f);
        search.setTextColor(COLOR_TEXT);
        search.setHintTextColor(COLOR_TEXT_SECONDARY);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(dialogContext, 14), 0, dp(dialogContext, 14), 0);
        search.setBackground(background(COLOR_PANEL, dp(dialogContext, 10),
                COLOR_BORDER, dp(dialogContext, 1)));

        final TextView categoryFilter = text(
                dialogContext, "All items  \u25BE", 12, COLOR_ACCENT, true);
        categoryFilter.setGravity(Gravity.CENTER);
        categoryFilter.setSingleLine(true);
        categoryFilter.setPadding(dp(dialogContext, 10), 0, dp(dialogContext, 10), 0);
        categoryFilter.setBackground(background(COLOR_PANEL, dp(dialogContext, 10),
                COLOR_BORDER, dp(dialogContext, 1)));

        final EditText valueInput = new EditText(dialogContext);
        valueInput.setSingleLine(true);
        valueInput.setHint(compactLandscape ? "Value" : "Amount");
        valueInput.setText("1");
        valueInput.setSelectAllOnFocus(true);
        valueInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        valueInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        valueInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        valueInput.setTextSize(13f);
        valueInput.setTextColor(COLOR_TEXT);
        valueInput.setHintTextColor(COLOR_TEXT_SECONDARY);
        valueInput.setGravity(Gravity.CENTER);
        valueInput.setPadding(dp(dialogContext, 6), 0, dp(dialogContext, 6), 0);
        valueInput.setBackground(background(COLOR_PANEL, dp(dialogContext, 10),
                COLOR_BORDER, dp(dialogContext, 1)));
        valueInput.setContentDescription("Amount from 1 to 100");

        if (compactLandscape) {
            LinearLayout header = horizontal(dialogContext);
            header.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout titleBlock = vertical(dialogContext);
            titleBlock.addView(title, matchWrap());
            LinearLayout.LayoutParams compactSubtitleParams = matchWrap();
            compactSubtitleParams.topMargin = dp(dialogContext, 2);
            titleBlock.addView(subtitle, compactSubtitleParams);
            header.addView(titleBlock, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
            LinearLayout.LayoutParams compactSearchParams = new LinearLayout.LayoutParams(
                    0, dp(dialogContext, 42), 1.05f);
            compactSearchParams.leftMargin = dp(dialogContext, 14);
            header.addView(search, compactSearchParams);
            LinearLayout.LayoutParams compactCategoryParams = new LinearLayout.LayoutParams(
                    dp(dialogContext, 96), dp(dialogContext, 42));
            compactCategoryParams.leftMargin = dp(dialogContext, 8);
            header.addView(categoryFilter, compactCategoryParams);
            LinearLayout.LayoutParams compactValueParams = new LinearLayout.LayoutParams(
                    dp(dialogContext, 66), dp(dialogContext, 42));
            compactValueParams.leftMargin = dp(dialogContext, 8);
            header.addView(valueInput, compactValueParams);
            LinearLayout.LayoutParams headerParams = matchWrap();
            headerParams.bottomMargin = dp(dialogContext, 4);
            root.addView(header, headerParams);
        } else {
            root.addView(title, matchWrap());
            LinearLayout.LayoutParams subtitleParams = matchWrap();
            subtitleParams.topMargin = dp(dialogContext, 3);
            subtitleParams.bottomMargin = dp(dialogContext, 12);
            root.addView(subtitle, subtitleParams);
            root.addView(search, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(dialogContext, 46)));

            LinearLayout filters = horizontal(dialogContext);
            LinearLayout.LayoutParams filtersParams = matchWrap();
            filtersParams.topMargin = dp(dialogContext, 8);
            root.addView(filters, filtersParams);
            filters.addView(categoryFilter, new LinearLayout.LayoutParams(
                    0, dp(dialogContext, 44), 1f));
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    dp(dialogContext, 116), dp(dialogContext, 44));
            valueParams.leftMargin = dp(dialogContext, 8);
            filters.addView(valueInput, valueParams);
        }

        LinearLayout selectionBar = horizontal(dialogContext);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectionBarParams = matchWrap();
        selectionBarParams.topMargin = dp(dialogContext, compactLandscape ? 3 : 8);
        selectionBarParams.bottomMargin = dp(dialogContext, compactLandscape ? 2 : 6);
        root.addView(selectionBar, selectionBarParams);

        final TextView selectedCount = text(
                dialogContext, "No items selected", 12, COLOR_TEXT_SECONDARY, true);
        selectionBar.addView(selectedCount,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView selectVisible = actionText(dialogContext, "Select shown");
        selectionBar.addView(selectVisible, wrapWrap());

        final TextView clear = actionText(dialogContext, "Clear");
        LinearLayout.LayoutParams clearParams = wrapWrap();
        clearParams.leftMargin = dp(dialogContext, 16);
        selectionBar.addView(clear, clearParams);

        final Set<Integer> selectedIds = new LinkedHashSet<>();
        final ItemAdapter adapter = new ItemAdapter(
                dialogContext, items, selectedIds, compactLandscape);
        final ListView list = new ListView(dialogContext);
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(dp(dialogContext, 5));
        list.setSelector(android.R.color.transparent);
        list.setVerticalScrollBarEnabled(true);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = horizontal(dialogContext);
        footer.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.topMargin = dp(dialogContext, compactLandscape ? 7 : 12);
        root.addView(footer, footerParams);

        Button cancel = commandButton(dialogContext, "Cancel", COLOR_PANEL);
        footer.addView(cancel, new LinearLayout.LayoutParams(
                0, dp(dialogContext, compactLandscape ? 40 : 46), 1f));

        final Button spawn = commandButton(dialogContext, "Add", COLOR_ACTION);
        LinearLayout.LayoutParams spawnParams = new LinearLayout.LayoutParams(
                0, dp(dialogContext, compactLandscape ? 40 : 46), 1.35f);
        spawnParams.leftMargin = dp(dialogContext, 10);
        footer.addView(spawn, spawnParams);

        final Runnable updateSelectionUi = new Runnable() {
            @Override
            public void run() {
                int count = selectedIds.size();
                selectedCount.setText(count == 0
                        ? "No items selected"
                        : count + (count == 1 ? " item selected" : " items selected"));
                spawn.setEnabled(count > 0);
                spawn.setAlpha(count > 0 ? 1f : 0.45f);
                clear.setAlpha(count > 0 ? 1f : 0.45f);
            }
        };
        adapter.setSelectionChangedListener(updateSelectionUi);
        updateSelectionUi.run();

        categoryFilter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View anchor) {
                PopupMenu popup = new PopupMenu(dialogContext, anchor);
                for (String category : categoryFilters(items)) popup.getMenu().add(category);
                popup.setOnMenuItemClickListener(item -> {
                    String category = item.getTitle().toString();
                    adapter.setCategoryFilter(category);
                    categoryFilter.setText(shortCategoryLabel(category) + "  \u25BE");
                    return true;
                });
                popup.show();
            }
        });

        valueInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) valueInput.setText(String.valueOf(parseRequestedValue(valueInput)));
            }
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                adapter.filter(value == null ? "" : value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            hideKeyboard(view);
            view.clearFocus();
            return true;
        });
        valueInput.setOnEditorActionListener((view, actionId, event) -> {
            valueInput.setText(String.valueOf(parseRequestedValue(valueInput)));
            hideKeyboard(view);
            view.clearFocus();
            return true;
        });

        selectVisible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adapter.selectVisible();
            }
        });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedIds.isEmpty()) return;
                selectedIds.clear();
                adapter.notifyDataSetChanged();
                updateSelectionUi.run();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        spawn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectedIds.isEmpty()) return;
                int[] ids = new int[selectedIds.size()];
                int[] values = new int[selectedIds.size()];
                int requestedValue = parseRequestedValue(valueInput);
                int index = 0;
                for (Item item : items) {
                    if (selectedIds.contains(item.id)) {
                        ids[index] = item.id;
                        values[index] = requestedValue;
                        index++;
                    }
                }
                hideKeyboard(search);
                dialog.dismiss();
                backend.addItems(sourceContext, ids, values);
            }
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.72f;
        window.setAttributes(attributes);
        if (!(dialogContext instanceof Activity)) {
            window.setType(Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE);
        }
        dialog.show();

        int horizontalMargin = dp(dialogContext, 24);
        int verticalMargin = dp(dialogContext, compactLandscape ? 12 : 24);
        int width = Math.min(metrics.widthPixels - horizontalMargin * 2,
                dp(dialogContext, 560));
        int height = Math.min(metrics.heightPixels - verticalMargin * 2,
                dp(dialogContext, 680));
        window.setLayout(Math.max(1, width), Math.max(1, height));
        window.setGravity(Gravity.CENTER);
    }

    private static List<Item> loadItems(Backend backend) {
        List<Item> items = new ArrayList<>();
        String[] rows = backend.loadItemCatalog();
        for (String row : rows) {
            if (row == null) continue;
            String[] fields = row.split("\\t", -1);
            if (fields.length != 3) continue;
            try {
                int type = Integer.parseInt(fields[2]);
                items.add(new Item(Integer.parseInt(fields[0]), fields[1],
                        backend.categoryFor(type)));
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private static int parseRequestedValue(EditText input) {
        int value = 1;
        try {
            value = Integer.parseInt(input.getText().toString().trim());
        } catch (Throwable ignored) {
        }
        return Math.max(1, Math.min(999, value));
    }

    private static String shortCategoryLabel(String category) {
        if (CATEGORY_ALL.equals(category)) return "All items";
        return category;
    }

    private static List<String> categoryFilters(List<Item> items) {
        Set<String> categories = new LinkedHashSet<>();
        categories.add(CATEGORY_ALL);
        for (Item item : items) categories.add(item.category);
        return new ArrayList<>(categories);
    }

    private static String categoryAliases(String category) {
        if ("Weapons".equals(category)) return "weapon sword bow ranged melee";
        if ("Axes".equals(category) || "Pickaxes".equals(category)) return "tool mining";
        if ("Medicine".equals(category)) return "health healing medical";
        if ("Resources".equals(category)) return "material crafting resource";
        if ("Food".equals(category) || "Water".equals(category)) return "consume survival";
        return "item inventory";
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

    private static TextView text(
            Context context, String value, float size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private static TextView actionText(Context context, String value) {
        TextView view = text(context, value, 12, COLOR_ACCENT, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 4), dp(context, 8), dp(context, 4), dp(context, 8));
        return view;
    }

    private static Button commandButton(Context context, String label, int color) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(13f);
        button.setTextColor(COLOR_TEXT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(background(color, dp(context, 10),
                COLOR_BORDER, dp(context, 1)));
        return button;
    }

    private static GradientDrawable background(int color, int radius, int strokeColor,
                                               int strokeWidth) {
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

    private static LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class Item {
        final int id;
        final String name;
        final String category;
        final String searchable;

        Item(int id, String name, String category) {
            this.id = id;
            this.name = name;
            this.category = category == null || category.trim().isEmpty()
                    ? "Other" : category.trim();
            this.searchable = (name + " " + id + " " + category + " "
                    + categoryAliases(category))
                    .toLowerCase(Locale.ROOT);
        }
    }

    private static final class ItemAdapter extends BaseAdapter {
        private final Context context;
        private final List<Item> allItems;
        private final List<Item> visibleItems = new ArrayList<>();
        private final Set<Integer> selectedIds;
        private final boolean compactRows;
        private String query = "";
        private String categoryFilter = CATEGORY_ALL;
        private Runnable selectionChangedListener;

        ItemAdapter(Context context, List<Item> allItems, Set<Integer> selectedIds,
                    boolean compactRows) {
            this.context = context;
            this.allItems = allItems;
            this.selectedIds = selectedIds;
            this.compactRows = compactRows;
            visibleItems.addAll(allItems);
        }

        void setSelectionChangedListener(Runnable listener) {
            selectionChangedListener = listener;
        }

        void filter(String rawQuery) {
            query = rawQuery.trim().toLowerCase(Locale.ROOT);
            rebuildVisibleItems();
        }

        void setCategoryFilter(String category) {
            categoryFilter = category == null ? CATEGORY_ALL : category;
            rebuildVisibleItems();
        }

        private void rebuildVisibleItems() {
            visibleItems.clear();
            for (Item item : allItems) {
                if (!matchesCategory(item) || !matchesQuery(item)) continue;
                visibleItems.add(item);
            }
            notifyDataSetChanged();
        }

        private boolean matchesCategory(Item item) {
            if (CATEGORY_ALL.equals(categoryFilter)) return true;
            return categoryFilter.equals(item.category);
        }

        private boolean matchesQuery(Item item) {
            if (query.isEmpty()) return true;
            String[] terms = query.split("\\s+");
            for (String term : terms) {
                if (!term.isEmpty() && !item.searchable.contains(term)) return false;
            }
            return true;
        }

        void selectVisible() {
            for (Item item : visibleItems) selectedIds.add(item.id);
            notifyDataSetChanged();
            if (selectionChangedListener != null) selectionChangedListener.run();
        }

        @Override
        public int getCount() {
            return visibleItems.size();
        }

        @Override
        public Item getItem(int position) {
            return visibleItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).id;
        }

        @Override
        public View getView(int position, View recycled, ViewGroup parent) {
            final Item item = getItem(position);
            final Holder holder;
            if (recycled instanceof LinearLayout && recycled.getTag() instanceof Holder) {
                holder = (Holder) recycled.getTag();
            } else {
                recycled = createHolderView();
                holder = (Holder) recycled.getTag();
            }

            boolean selected = selectedIds.contains(item.id);
            holder.id.setText(String.valueOf(item.id));
            holder.name.setText(item.name);
            holder.details.setText(item.category + "  \u2022  ID " + item.id);
            holder.check.setChecked(selected);
            holder.root.setBackground(background(
                    selected ? COLOR_HOLDER_SELECTED : COLOR_HOLDER,
                    dp(context, 10), selected ? COLOR_ACCENT : COLOR_BORDER,
                    dp(context, 1)));
            holder.id.setBackground(background(
                    selected ? COLOR_ACCENT : COLOR_PANEL,
                    dp(context, 8), selected ? COLOR_ACCENT : COLOR_BORDER,
                    dp(context, 1)));
            holder.id.setTextColor(selected ? Color.parseColor("#FF2B2318") : COLOR_ACCENT);

            holder.root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selectedIds.contains(item.id)) selectedIds.remove(item.id);
                    else selectedIds.add(item.id);
                    notifyDataSetChanged();
                    if (selectionChangedListener != null) selectionChangedListener.run();
                }
            });
            return recycled;
        }

        private View createHolderView() {
            LinearLayout root = horizontal(context);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(context, 8), dp(context, compactRows ? 4 : 7),
                    dp(context, 8), dp(context, compactRows ? 4 : 7));
            root.setMinimumHeight(dp(context, compactRows ? 52 : 62));

            TextView id = text(context, "", 12, COLOR_ACCENT, true);
            id.setGravity(Gravity.CENTER);
            root.addView(id, new LinearLayout.LayoutParams(
                    dp(context, compactRows ? 42 : 48),
                    dp(context, compactRows ? 42 : 48)));

            LinearLayout labels = vertical(context);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsParams.leftMargin = dp(context, 12);
            root.addView(labels, labelsParams);

            TextView name = text(context, "", compactRows ? 13 : 14, COLOR_TEXT, true);
            name.setSingleLine(true);
            labels.addView(name, matchWrap());
            TextView details = text(context, "", 11, COLOR_TEXT_SECONDARY, false);
            LinearLayout.LayoutParams detailParams = matchWrap();
            detailParams.topMargin = dp(context, 2);
            labels.addView(details, detailParams);

            CheckBox check = new CheckBox(context);
            check.setClickable(false);
            check.setFocusable(false);
            if (Build.VERSION.SDK_INT >= 21) {
                int[][] states = {
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                };
                int[] colors = {COLOR_ACCENT, COLOR_TEXT_SECONDARY};
                check.setButtonTintList(new ColorStateList(states, colors));
            }
            root.addView(check, wrapWrap());

            root.setTag(new Holder(root, id, name, details, check));
            return root;
        }
    }

    private static final class Holder {
        final LinearLayout root;
        final TextView id;
        final TextView name;
        final TextView details;
        final CheckBox check;

        Holder(LinearLayout root, TextView id, TextView name,
               TextView details, CheckBox check) {
            this.root = root;
            this.id = id;
            this.name = name;
            this.details = details;
            this.check = check;
        }
    }
}
