package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.mlkit.nl.translate.TranslateLanguage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ModelManagementActivity extends AppCompatActivity {
    private static final String BUILT_IN_LANGUAGE = TranslateLanguage.ENGLISH;

    private final List<String> supportedLanguages = new ArrayList<>();
    private final Set<String> downloadedModels = new HashSet<>();
    private final List<String> availableModels = new ArrayList<>();
    private final List<String> downloadedModelsList = new ArrayList<>();

    private TranslationViewModel viewModel;
    private ListView availableModelsListView;
    private ListView downloadedModelsListView;
    private Button downloadButton;
    private Button deleteButton;
    private TextView availableModelsEmptyText;
    private TextView downloadedModelsEmptyText;
    private TextView modelStatusText;
    private View operationProgress;
    private ArrayAdapter<String> availableModelsAdapter;
    private ArrayAdapter<String> downloadedModelsAdapter;
    @Nullable private String selectedAvailableLanguage;
    @Nullable private String selectedDownloadedLanguage;
    private boolean isBusy;

    public static Intent createIntent(Context context) {
        return new Intent(context, ModelManagementActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_management);
        bindViews();
        setupViewModel();
        setupLanguageLists();
        setupActions();
        refreshDownloadedModels();
    }

    private void bindViews() {
        MaterialToolbar toolbar = findViewById(R.id.modelManagementToolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        availableModelsListView = findViewById(R.id.availableModelsList);
        downloadedModelsListView = findViewById(R.id.downloadedModelsList);
        downloadButton = findViewById(R.id.downloadTargetModelButton);
        deleteButton = findViewById(R.id.deleteTargetModelButton);
        availableModelsEmptyText = findViewById(R.id.availableModelsEmptyText);
        downloadedModelsEmptyText = findViewById(R.id.downloadedModelsEmptyText);
        modelStatusText = findViewById(R.id.modelStatusText);
        operationProgress = findViewById(R.id.modelOperationProgress);

        Collections.addAll(
                supportedLanguages, getResources().getStringArray(R.array.language_codes));
    }

    private void setupViewModel() {
        TranslationRepository translationRepository =
                new TranslationRepository(
                        new io.github.godsarmy.mlhtmltranslator.api.MlKitHtmlTranslator(
                                getApplicationContext(), null));
        viewModel = new TranslationViewModel(translationRepository, new ModelLifecycleManager());
    }

    private void setupLanguageLists() {
        availableModelsAdapter =
                new ArrayAdapter<>(
                        this, android.R.layout.simple_list_item_single_choice, availableModels) {
                    @NonNull
                    @Override
                    public View getView(
                            int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        bindDisplayLabel(view, getItem(position));
                        return view;
                    }
                };

        downloadedModelsAdapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_single_choice,
                        downloadedModelsList) {
                    @Override
                    public boolean areAllItemsEnabled() {
                        return false;
                    }

                    @Override
                    public boolean isEnabled(int position) {
                        String language = getItem(position);
                        return !BUILT_IN_LANGUAGE.equals(normalizeLanguageCode(language));
                    }

                    @NonNull
                    @Override
                    public View getView(
                            int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        bindDisplayLabel(view, getItem(position));
                        boolean enabled = isEnabled(position);
                        view.setEnabled(enabled);
                        view.setAlpha(enabled ? 1f : 0.45f);
                        return view;
                    }
                };

        availableModelsListView.setAdapter(availableModelsAdapter);
        downloadedModelsListView.setAdapter(downloadedModelsAdapter);
    }

    private void bindDisplayLabel(@NonNull View view, @Nullable String language) {
        if (!(view instanceof TextView) || language == null) {
            return;
        }
        ((TextView) view).setText(formatLanguageLabel(language));
    }

    private String formatLanguageLabel(String languageCode) {
        String normalized = normalizeLanguageCode(languageCode);
        if (normalized == null) {
            return languageCode;
        }
        String displayName = new Locale(normalized).getDisplayLanguage(Locale.getDefault());
        if (displayName == null || displayName.trim().isEmpty()) {
            return languageCode;
        }
        return displayName + " (" + languageCode + ")";
    }

    private void setupActions() {
        availableModelsListView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedAvailableLanguage = availableModels.get(position);
                    updateUiState();
                });

        downloadedModelsListView.setOnItemClickListener(
                (parent, view, position, id) -> {
                    selectedDownloadedLanguage = downloadedModelsList.get(position);
                    updateUiState();
                });

        downloadButton.setOnClickListener(v -> downloadTargetModel());
        deleteButton.setOnClickListener(v -> deleteTargetModel());
    }

    private void refreshDownloadedModels() {
        viewModel.refreshDownloadedModels(
                new ModelLifecycleManager.RefreshCallback() {
                    @Override
                    public void onComplete() {
                        downloadedModels.clear();
                        downloadedModels.addAll(viewModel.downloadedModels());
                        rebuildLanguageLists();
                        updateUiState();
                    }

                    @Override
                    public void onError(@NonNull String reason) {
                        downloadedModels.clear();
                        downloadedModels.addAll(viewModel.downloadedModels());
                        rebuildLanguageLists();
                        updateUiState();
                    }
                });
    }

    private void rebuildLanguageLists() {
        availableModels.clear();
        downloadedModelsList.clear();

        for (String language : supportedLanguages) {
            String normalized = normalizeLanguageCode(language);
            if (BUILT_IN_LANGUAGE.equals(normalized)) {
                downloadedModelsList.add(language);
            } else if (normalized != null && downloadedModels.contains(normalized)) {
                downloadedModelsList.add(language);
            } else {
                availableModels.add(language);
            }
        }

        if (selectedAvailableLanguage == null
                || !availableModels.contains(selectedAvailableLanguage)) {
            selectedAvailableLanguage = availableModels.isEmpty() ? null : availableModels.get(0);
        }

        if (selectedDownloadedLanguage == null
                || !downloadedModelsList.contains(selectedDownloadedLanguage)) {
            selectedDownloadedLanguage = firstDeletableDownloadedLanguage();
        } else if (BUILT_IN_LANGUAGE.equals(normalizeLanguageCode(selectedDownloadedLanguage))) {
            selectedDownloadedLanguage = firstDeletableDownloadedLanguage();
        }

        availableModelsAdapter.notifyDataSetChanged();
        downloadedModelsAdapter.notifyDataSetChanged();
        updateListSelection(availableModelsListView, availableModels, selectedAvailableLanguage);
        updateListSelection(
                downloadedModelsListView, downloadedModelsList, selectedDownloadedLanguage);
    }

    @Nullable
    private String firstDeletableDownloadedLanguage() {
        for (String language : downloadedModelsList) {
            if (!BUILT_IN_LANGUAGE.equals(normalizeLanguageCode(language))) {
                return language;
            }
        }
        return null;
    }

    private static void updateListSelection(
            ListView listView, List<String> values, @Nullable String selectedValue) {
        listView.clearChoices();
        if (selectedValue == null) {
            return;
        }
        int index = values.indexOf(selectedValue);
        if (index >= 0) {
            listView.setItemChecked(index, true);
        }
    }

    private void setBusy(boolean busy) {
        isBusy = busy;
        updateUiState();
    }

    private void updateUiState() {
        boolean hasAvailableSelection = selectedAvailableLanguage != null;
        boolean hasDownloadedSelection =
                selectedDownloadedLanguage != null
                        && !BUILT_IN_LANGUAGE.equals(
                                normalizeLanguageCode(selectedDownloadedLanguage));

        operationProgress.setVisibility(isBusy ? View.VISIBLE : View.GONE);
        availableModelsListView.setEnabled(!isBusy);
        downloadedModelsListView.setEnabled(!isBusy);
        downloadButton.setEnabled(!isBusy && hasAvailableSelection);
        deleteButton.setEnabled(!isBusy && hasDownloadedSelection);
        availableModelsEmptyText.setVisibility(
                availableModels.isEmpty() ? View.VISIBLE : View.GONE);
        downloadedModelsEmptyText.setVisibility(
                downloadedModelsList.isEmpty() ? View.VISIBLE : View.GONE);
        modelStatusText.setText(
                getString(
                        R.string.model_management_status,
                        downloadedModelsList.size(),
                        availableModels.size()));
    }

    private void downloadTargetModel() {
        if (selectedAvailableLanguage == null) {
            return;
        }
        String language = selectedAvailableLanguage;
        setBusy(true);
        viewModel.downloadModel(
                language,
                new ModelLifecycleManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(
                                () -> {
                                    setBusy(false);
                                    refreshDownloadedModels();
                                    Toast.makeText(
                                                    ModelManagementActivity.this,
                                                    getString(
                                                            R.string.model_download_success_message,
                                                            language),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                });
                    }

                    @Override
                    public void onFailure(@NonNull String reason) {
                        runOnUiThread(
                                () -> {
                                    setBusy(false);
                                    Toast.makeText(
                                                    ModelManagementActivity.this,
                                                    getString(
                                                            R.string.model_download_failed_message,
                                                            language),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                });
                    }
                });
    }

    private void deleteTargetModel() {
        if (selectedDownloadedLanguage == null) {
            return;
        }
        String language = selectedDownloadedLanguage;
        String normalizedLanguageCode = normalizeLanguageCode(language);
        if (BUILT_IN_LANGUAGE.equals(normalizedLanguageCode)) {
            Toast.makeText(this, R.string.model_built_in_not_deletable, Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        viewModel.deleteModel(
                language,
                new ModelLifecycleManager.ActionCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(
                                () -> {
                                    setBusy(false);
                                    refreshDownloadedModels();
                                    Toast.makeText(
                                                    ModelManagementActivity.this,
                                                    getString(
                                                            R.string.model_delete_success_message,
                                                            language),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                });
                    }

                    @Override
                    public void onFailure(@NonNull String reason) {
                        runOnUiThread(
                                () -> {
                                    setBusy(false);
                                    Toast.makeText(
                                                    ModelManagementActivity.this,
                                                    getString(
                                                            R.string.model_delete_failed_message,
                                                            language),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                });
                    }
                });
    }

    @Nullable
    private static String normalizeLanguageCode(@Nullable String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return null;
        }

        String normalizedInput = languageCode.trim();
        String translatedLanguage = TranslateLanguage.fromLanguageTag(normalizedInput);
        if (translatedLanguage != null) {
            return translatedLanguage;
        }

        int separatorIndex = normalizedInput.indexOf('-');
        if (separatorIndex < 0) {
            separatorIndex = normalizedInput.indexOf('_');
        }
        if (separatorIndex > 0) {
            return TranslateLanguage.fromLanguageTag(normalizedInput.substring(0, separatorIndex));
        }

        return null;
    }
}
