package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class HelpActivity extends AppCompatActivity {

    public static Intent createIntent(AppCompatActivity activity) {
        return new Intent(activity, HelpActivity.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        setupToolbar();
        setupButtons();
        bindHelpDocument();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.helpToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupButtons() {
        MaterialButton openProjectPageButton = findViewById(R.id.openProjectPageButton);
        MaterialButton sendFeedbackButton = findViewById(R.id.sendFeedbackButton);

        openProjectPageButton.setOnClickListener(v -> openProjectPage());
        sendFeedbackButton.setOnClickListener(v -> sendFeedback());
    }

    private void bindHelpDocument() {
        TextView helpDocumentText = findViewById(R.id.helpDocumentText);
        helpDocumentText.setText(readHelpDocument());
    }

    private CharSequence readHelpDocument() {
        try (InputStream inputStream = getAssets().open("docs/help_feedback.txt");
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString().trim();
        } catch (IOException error) {
            return getString(R.string.help_document_unavailable);
        }
    }

    private void openProjectPage() {
        Intent browserIntent =
                new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.project_page_url)));
        try {
            startActivity(browserIntent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.project_page_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendFeedback() {
        Intent emailIntent =
                new Intent(Intent.ACTION_SENDTO)
                        .setData(Uri.parse("mailto:"))
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject));
        try {
            startActivity(
                    Intent.createChooser(emailIntent, getString(R.string.feedback_email_chooser)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.feedback_not_available, Toast.LENGTH_SHORT).show();
        }
    }
}
