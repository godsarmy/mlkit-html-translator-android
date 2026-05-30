package io.github.godsarmy.mlhtmltranslator.sample;

import java.util.List;

public final class ExplainPageItem {
    private final String title;
    private final List<ExplainPageRow> rows;
    private final ToggleRows toggleRows;

    public ExplainPageItem(String title, List<ExplainPageRow> rows) {
        this.title = title;
        this.rows = rows;
        this.toggleRows = null;
    }

    public ExplainPageItem(String title, ToggleRows toggleRows) {
        this.title = title;
        this.rows = toggleRows.getPrimaryRows();
        this.toggleRows = toggleRows;
    }

    public String getTitle() {
        return title;
    }

    public List<ExplainPageRow> getRows() {
        return rows;
    }

    public boolean hasToggleRows() {
        return toggleRows != null;
    }

    public ToggleRows getToggleRows() {
        return toggleRows;
    }

    public static final class ToggleRows {
        private final String primaryLabel;
        private final List<ExplainPageRow> primaryRows;
        private final String secondaryLabel;
        private final List<ExplainPageRow> secondaryRows;

        public ToggleRows(
                String primaryLabel,
                List<ExplainPageRow> primaryRows,
                String secondaryLabel,
                List<ExplainPageRow> secondaryRows) {
            this.primaryLabel = primaryLabel;
            this.primaryRows = primaryRows;
            this.secondaryLabel = secondaryLabel;
            this.secondaryRows = secondaryRows;
        }

        public String getPrimaryLabel() {
            return primaryLabel;
        }

        public List<ExplainPageRow> getPrimaryRows() {
            return primaryRows;
        }

        public String getSecondaryLabel() {
            return secondaryLabel;
        }

        public List<ExplainPageRow> getSecondaryRows() {
            return secondaryRows;
        }
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
