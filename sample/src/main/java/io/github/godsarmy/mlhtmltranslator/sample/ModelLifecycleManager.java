package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * App-layer utility for ML model lifecycle ownership.
 *
 * <p>In production this should wrap ML Kit APIs: RemoteModelManager, TranslateRemoteModel, and
 * DownloadConditions.
 */
public final class ModelLifecycleManager {

    private static final String PREFS_NAME = "mlhtmltranslator_sample_models";
    private static final String KEY_DOWNLOADED_MODELS = "downloaded_models";

    private final Set<String> downloadedModels = Collections.synchronizedSet(new HashSet<>());
    private final SharedPreferences preferences;

    public ModelLifecycleManager(@NonNull Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedModels = preferences.getStringSet(KEY_DOWNLOADED_MODELS, null);
        if (savedModels != null && !savedModels.isEmpty()) {
            downloadedModels.addAll(savedModels);
        } else {
            // Sample default: English is present.
            downloadedModels.add("en");
            persistModels();
        }
    }

    public boolean isModelAvailable(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }
        return downloadedModels.contains(languageCode.trim().toLowerCase());
    }

    public boolean downloadModel(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }
        boolean changed = downloadedModels.add(languageCode.trim().toLowerCase());
        if (changed) {
            persistModels();
        }
        return changed;
    }

    public boolean deleteModel(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }
        boolean changed = downloadedModels.remove(languageCode.trim().toLowerCase());
        if (changed) {
            persistModels();
        }
        return changed;
    }

    private void persistModels() {
        preferences
                .edit()
                .putStringSet(KEY_DOWNLOADED_MODELS, new HashSet<>(downloadedModels))
                .apply();
    }
}
