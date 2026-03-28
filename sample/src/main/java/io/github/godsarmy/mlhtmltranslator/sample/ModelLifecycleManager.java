package io.github.godsarmy.mlhtmltranslator.sample;

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

    private final Set<String> downloadedModels = Collections.synchronizedSet(new HashSet<>());

    public ModelLifecycleManager() {
        // Sample default: English is present.
        downloadedModels.add("en");
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
        return downloadedModels.add(languageCode.trim().toLowerCase());
    }

    public boolean deleteModel(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return false;
        }
        return downloadedModels.remove(languageCode.trim().toLowerCase());
    }
}
