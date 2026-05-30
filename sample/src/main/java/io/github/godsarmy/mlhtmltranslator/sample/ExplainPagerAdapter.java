package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public final class ExplainPagerAdapter
        extends RecyclerView.Adapter<ExplainPagerAdapter.PageViewHolder> {
    private final LayoutInflater inflater;
    private final List<ExplainPageItem> items = new ArrayList<>();

    public ExplainPagerAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    public void setItems(List<ExplainPageItem> pageItems) {
        items.clear();
        items.addAll(pageItems);
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
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class PageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout content;
        private final TextView emptyText;

        PageViewHolder(@NonNull View itemView) {
            super(itemView);
            content = itemView.findViewById(R.id.explainPageContent);
            emptyText = itemView.findViewById(R.id.explainPageEmptyText);
        }

        void bind(ExplainPageItem item) {
            Context context = itemView.getContext();
            content.removeViews(1, Math.max(0, content.getChildCount() - 1));

            if (item.getRows().isEmpty()) {
                emptyText.setText(R.string.explain_none);
                emptyText.setVisibility(View.VISIBLE);
                return;
            }
            emptyText.setVisibility(View.GONE);

            for (int index = 0; index < item.getRows().size(); index++) {
                ExplainPageItem.ExplainPageRow row = item.getRows().get(index);
                TextView value = createValue(context, index > 0);
                value.setText(row.getValue());
                value.setOnLongClickListener(
                        v -> {
                            copyToClipboard(context, row.getLabel(), row.getValue());
                            return true;
                        });
                content.addView(value);
            }
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
