package io.github.godsarmy.mlhtmltranslator.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class LineNumberGutterViewTest {
    @Test
    public void visibleGutterResizesWhenFormattingAddsLines() {
        try (ActivityScenario<MainActivity> scenario =
                ActivityScenario.launch(MainActivity.class)) {
            LineNumberGutterView[] gutter = new LineNumberGutterView[1];
            TextView[] content = new TextView[1];
            scenario.onActivity(
                    activity -> {
                        LinearLayout row = new LinearLayout(activity);
                        gutter[0] = new LineNumberGutterView(activity);
                        content[0] = new TextView(activity);
                        content[0].setHorizontallyScrolling(true);
                        content[0].setText("line\n".repeat(8) + "line");
                        gutter[0].bindTo(content[0]);
                        row.addView(gutter[0], new LinearLayout.LayoutParams(-2, -1));
                        row.addView(content[0], new LinearLayout.LayoutParams(0, -1, 1));
                        activity.setContentView(row);
                    });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            int[] singleDigitWidth = new int[1];
            scenario.onActivity(activity -> singleDigitWidth[0] = gutter[0].getWidth());
            assertTrue(singleDigitWidth[0] > 0);
            for (int lines : new int[] {10, 99, 100, 9, 100, 10}) {
                scenario.onActivity(
                        activity -> content[0].setText("line\n".repeat(lines - 1) + "line"));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(
                        activity -> {
                            assertEquals(View.VISIBLE, gutter[0].getVisibility());
                            int digits = String.valueOf(lines).length();
                            // Monospace digits must all fit, even when the gutter was already
                            // visible.
                            assertTrue(gutter[0].getWidth() >= (singleDigitWidth[0] - 1) * digits);
                            assertTrue(gutter[0].getWidth() <= singleDigitWidth[0] * digits);
                        });
            }
        }
    }
}
