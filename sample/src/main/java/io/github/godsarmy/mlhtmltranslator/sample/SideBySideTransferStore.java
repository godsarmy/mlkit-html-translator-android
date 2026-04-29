package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

public final class SideBySideTransferStore {
    public static final int INLINE_THRESHOLD_BYTES = 128 * 1024;

    private static final String EXTRA_SOURCE_HTML = "extra_source_html";
    private static final String EXTRA_TRANSLATED_HTML = "extra_translated_html";
    private static final String EXTRA_TRANSFER_MODE = "extra_transfer_mode";
    private static final String EXTRA_PAYLOAD_FILE_PATH = "extra_payload_file_path";
    private static final String TRANSFER_MODE_INLINE = "inline";
    private static final String TRANSFER_MODE_FILE = "file";
    private static final String JSON_SOURCE_KEY = "source";
    private static final String JSON_TRANSLATED_KEY = "translated";
    private static final String PAYLOAD_FILE_PREFIX = "side_by_side_";
    private static final String PAYLOAD_FILE_SUFFIX = ".json";

    private SideBySideTransferStore() {}

    public static Intent createIntent(Context context, String sourceHtml, String translatedHtml) {
        Intent intent = new Intent(context, SideBySideCompareActivity.class);
        String source = valueOrEmpty(sourceHtml);
        String translated = valueOrEmpty(translatedHtml);

        if (utf8Size(source, translated) <= INLINE_THRESHOLD_BYTES) {
            putInline(intent, source, translated);
            return intent;
        }

        try {
            File payloadFile =
                    File.createTempFile(
                            PAYLOAD_FILE_PREFIX, PAYLOAD_FILE_SUFFIX, context.getCacheDir());
            writePayload(payloadFile, source, translated);
            intent.putExtra(EXTRA_TRANSFER_MODE, TRANSFER_MODE_FILE);
            intent.putExtra(EXTRA_PAYLOAD_FILE_PATH, payloadFile.getAbsolutePath());
            return intent;
        } catch (IOException | JSONException ignored) {
            putInline(intent, source, translated);
            return intent;
        }
    }

    public static TransferPayload resolveFromIntent(Context context, Intent intent) {
        if (intent == null) {
            return TransferPayload.empty();
        }

        String mode = intent.getStringExtra(EXTRA_TRANSFER_MODE);
        if (TRANSFER_MODE_FILE.equals(mode)) {
            String filePath = intent.getStringExtra(EXTRA_PAYLOAD_FILE_PATH);
            if (filePath != null && !filePath.isEmpty()) {
                File payloadFile = new File(filePath);
                if (isInCacheDir(context, payloadFile)
                        && payloadFile.exists()
                        && payloadFile.isFile()) {
                    try {
                        return readPayload(payloadFile);
                    } catch (IOException | JSONException ignored) {
                        // fallback to inline extras
                    }
                }
            }
        }

        return new TransferPayload(
                valueOrEmpty(intent.getStringExtra(EXTRA_SOURCE_HTML)),
                valueOrEmpty(intent.getStringExtra(EXTRA_TRANSLATED_HTML)));
    }

    public static void cleanupIfBackedByFile(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        if (!TRANSFER_MODE_FILE.equals(intent.getStringExtra(EXTRA_TRANSFER_MODE))) {
            return;
        }
        String filePath = intent.getStringExtra(EXTRA_PAYLOAD_FILE_PATH);
        if (filePath == null || filePath.isEmpty()) {
            return;
        }
        File payloadFile = new File(filePath);
        if (isInCacheDir(context, payloadFile) && payloadFile.exists() && payloadFile.isFile()) {
            payloadFile.delete();
        }
    }

    private static void putInline(Intent intent, String source, String translated) {
        intent.putExtra(EXTRA_TRANSFER_MODE, TRANSFER_MODE_INLINE);
        intent.putExtra(EXTRA_SOURCE_HTML, source);
        intent.putExtra(EXTRA_TRANSLATED_HTML, translated);
        intent.removeExtra(EXTRA_PAYLOAD_FILE_PATH);
    }

    private static void writePayload(File payloadFile, String source, String translated)
            throws IOException, JSONException {
        JSONObject payload = new JSONObject();
        payload.put(JSON_SOURCE_KEY, source);
        payload.put(JSON_TRANSLATED_KEY, translated);
        try (BufferedWriter writer =
                new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(payloadFile), StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
        }
    }

    private static TransferPayload readPayload(File payloadFile) throws IOException, JSONException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new FileInputStream(payloadFile), StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                content.append(buffer, 0, read);
            }
        }
        JSONObject payload = new JSONObject(content.toString());
        return new TransferPayload(
                payload.optString(JSON_SOURCE_KEY, ""), payload.optString(JSON_TRANSLATED_KEY, ""));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int utf8Size(String source, String translated) {
        return source.getBytes(StandardCharsets.UTF_8).length
                + translated.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isInCacheDir(Context context, File file) {
        try {
            String cachePath = context.getCacheDir().getCanonicalPath();
            String filePath = file.getCanonicalPath();
            return filePath.startsWith(cachePath + File.separator);
        } catch (IOException ignored) {
            return false;
        }
    }

    public static final class TransferPayload {
        @NonNull public final String sourceHtml;
        @NonNull public final String translatedHtml;

        public TransferPayload(@NonNull String sourceHtml, @NonNull String translatedHtml) {
            this.sourceHtml = sourceHtml;
            this.translatedHtml = translatedHtml;
        }

        public static TransferPayload empty() {
            return new TransferPayload("", "");
        }
    }
}
