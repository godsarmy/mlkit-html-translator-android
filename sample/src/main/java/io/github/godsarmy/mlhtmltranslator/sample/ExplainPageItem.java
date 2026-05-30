package io.github.godsarmy.mlhtmltranslator.sample;

import java.util.List;

public final class ExplainPageItem {
    private final String title;
    private final List<ExplainPageRow> rows;

    public ExplainPageItem(String title, List<ExplainPageRow> rows) {
        this.title = title;
        this.rows = rows;
    }

    public String getTitle() {
        return title;
    }

    public List<ExplainPageRow> getRows() {
        return rows;
    }

    public static final class ExplainPageRow {
        private final String label;
        private final String value;

        public ExplainPageRow(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }
}
