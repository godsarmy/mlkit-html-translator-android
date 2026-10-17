package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Shared system-bar setup and safe content bounds for the sample activities. */
final class SampleEdgeToEdge {
    private SampleEdgeToEdge() {}

    static void enable(AppCompatActivity activity) {
        boolean darkTheme =
                (activity.getResources().getConfiguration().uiMode
                                & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        SystemBarStyle style =
                darkTheme
                        ? SystemBarStyle.dark(Color.TRANSPARENT)
                        : SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT);
        EdgeToEdge.enable(activity, style, style);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    static void applyContentInsets(AppCompatActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        applyInsets(content.getChildAt(0), true);
    }

    static void applyScrollingContentInsets(AppCompatActivity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        View root = content.getChildAt(0);
        // Scroll pages reserve their own bottom safe area inside the scrolling content.
        root.setPadding(root.getPaddingLeft(), root.getPaddingTop(), root.getPaddingRight(), 0);
        applyInsets(root, false);
    }

    private static void applyInsets(View root, boolean insetBottom) {
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(
                root,
                (view, windowInsets) -> {
                    Insets safeInsets =
                            windowInsets.getInsets(
                                    WindowInsetsCompat.Type.systemBars()
                                            | WindowInsetsCompat.Type.displayCutout()
                                            | WindowInsetsCompat.Type.ime());
                    view.setPadding(
                            left + safeInsets.left,
                            top + safeInsets.top,
                            right + safeInsets.right,
                            bottom + (insetBottom ? safeInsets.bottom : 0));
                    // Scrolling pages can handle the bottom inset inside their own padding.
                    return insetBottom ? WindowInsetsCompat.CONSUMED : windowInsets;
                });
        ViewCompat.requestApplyInsets(root);
    }
}
