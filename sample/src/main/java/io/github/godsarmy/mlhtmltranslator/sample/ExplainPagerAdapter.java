package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.List;

public final class ExplainPagerAdapter
        extends RecyclerView.Adapter<ExplainPagerAdapter.PageViewHolder> {
    private final LayoutInflater inflater;
    private final List<ExplainPageItem> items = new ArrayList<>();
    private final SparseBooleanArray showSecondaryByPosition = new SparseBooleanArray();

    public ExplainPagerAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<ExplainPageItem> pageItems) {
        items.clear();
        items.addAll(pageItems);
        showSecondaryByPosition.clear();
        notifyDataSetChanged();
    }

    public ExplainPageItem getItem(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.item_explain_page, parent, false);
        return new PageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        ExplainPageItem item = items.get(position);
        holder.bind(
                item,
                showSecondaryByPosition.get(position, item.hasToggleRows()),
                showSecondary -> {
                    showSecondaryByPosition.put(position, showSecondary);
                    notifyItemChanged(position);
                });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class PageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout content;
        private final TextView emptyText;

        interface OnToggleChange {
            void onChanged(boolean showSecondary);
        }

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.explainPageContent);
            emptyText = itemView.findViewById(R.id.explainPageEmptyText);
        }

        void bind(ExplainPageItem item, boolean showSecondary, OnToggleChange onToggleChange) {
            Context context = itemView.getContext();
            content.removeViews(1, Math.max(0, content.getChildCount() - 1));

            List<ExplainPageItem.ExplainPageRow> rows = item.getRows();
            if (item.hasToggleRows()) {
                ExplainPageItem.ToggleRows toggleRows = item.getToggleRows();
                rows = showSecondary ? toggleRows.getSecondaryRows() : toggleRows.getPrimaryRows();
                content.addView(createToggleSwitch(context, showSecondary, onToggleChange));
            }

            if (rows.isEmpty()) {
                emptyText.setText(R.string.explain_none);
                emptyText.setVisibility(View.VISIBLE);
                return;
            }
            emptyText.setVisibility(View.GONE);

            for (int index = 0; index < rows.size(); index++) {
                ExplainPageItem.ExplainPageRow row = rows.get(index);
                TextView value = createValue(context, index > 0 || item.hasToggleRows());
                value.setText(row.getValue());
                value.setOnLongClickListener(
                        v -> {
                            copyToClipboard(context, row.getLabel(), row.getValue());
                            return true;
                        });
                content.addView(value);
            }
        }

        private static View createToggleSwitch(
                Context context, boolean showSecondary, OnToggleChange onToggleChange) {
            SwitchMaterial toggle = new SwitchMaterial(context);
            LinearLayout.LayoutParams toggleParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            toggleParams.topMargin = dpToPx(context, 2);
            toggle.setLayoutParams(toggleParams);
            toggle.setText(R.string.explain_html_body_normalized_toggle);
            toggle.setTextColor(context.getColor(R.color.mlkit_on_background));
            toggle.setUseMaterialThemeColors(true);
            toggle.setChecked(showSecondary);
            toggle.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> onToggleChange.onChanged(isChecked));
            return toggle;
        }

        private static TextView createValue(Context context, boolean addTopMargin) {
            TextView textView = new TextView(context);
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            if (addTopMargin) {
                params.topMargin = dpToPx(context, 10);
            }
            textView.setLayoutParams(params);
            textView.setBackgroundResource(R.drawable.preview_box_background);
            textView.setPadding(
                    dpToPx(context, 12),
                    dpToPx(context, 12),
                    dpToPx(context, 12),
                    dpToPx(context, 12));
            textView.setTextColor(context.getColor(R.color.mlkit_on_background));
            textView.setTextIsSelectable(true);
            return textView;
        }

        private static int dpToPx(Context context, int dp) {
            return Math.round(dp * context.getResources().getDisplayMetrics().density);
        }

        private static void copyToClipboard(Context context, String label, String value) {
            ClipboardManager clipboardManager =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager == null) {
                return;
            }
            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(context, R.string.explain_copied_to_clipboard, Toast.LENGTH_SHORT)
                    .show();
        }
    }
}
