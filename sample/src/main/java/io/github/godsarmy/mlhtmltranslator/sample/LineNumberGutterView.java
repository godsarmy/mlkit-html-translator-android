package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;

public final class LineNumberGutterView extends View {
    private static final int DEFAULT_TEXT_COLOR = 0xFF757575;
    private final Paint lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private TextView attachedTextView;
    private int lineNumberDigits = 1;
    private final TextWatcher textWatcher =
            new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence text, int start, int before, int count) {
                    updateLineCount(text);
                }

                @Override
                public void afterTextChanged(Editable text) {}
            };

    public LineNumberGutterView(Context context) {
        super(context);
        initialize(context, null);
    }

    public LineNumberGutterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    public LineNumberGutterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
    }

    public void bindTo(TextView textView) {
        if (attachedTextView != null) {
            attachedTextView.removeTextChangedListener(textWatcher);
        }
        attachedTextView = textView;
        textView.addTextChangedListener(textWatcher);
        updateLineCount(textView.getText());
        applyTextMetricsFrom(textView);
        requestLayout();
        invalidate();
    }

    public void applyTextMetricsFrom(TextView contentView) {
        lineNumberPaint.setTypeface(Typeface.MONOSPACE);
        lineNumberPaint.setTextSize(contentView.getTextSize());
        lineNumberPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumberPaint.setLetterSpacing(contentView.getLetterSpacing());
        requestLayout();
        invalidate();
    }

    private void initialize(Context context, @Nullable AttributeSet attrs) {
        lineNumberPaint.setTypeface(Typeface.MONOSPACE);
        lineNumberPaint.setTextAlign(Paint.Align.RIGHT);
        lineNumberPaint.setColor(DEFAULT_TEXT_COLOR);
        if (attrs == null) {
            return;
        }
        TypedArray typedArray =
                context.obtainStyledAttributes(attrs, new int[] {android.R.attr.textColor});
        int textColor = typedArray.getColor(0, DEFAULT_TEXT_COLOR);
        typedArray.recycle();
        lineNumberPaint.setColor(textColor);
    }

    private void updateLineCount(CharSequence text) {
        // Raw comparison disables wrapping, so newlines determine the displayed line count.
        int lineCount = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                lineCount++;
            }
        }
        int digits = String.valueOf(lineCount).length();
        if (digits != lineNumberDigits) {
            lineNumberDigits = digits;
            requestLayout();
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float textWidth = lineNumberPaint.measureText("8".repeat(lineNumberDigits));
        int desiredWidth = getPaddingLeft() + getPaddingRight() + (int) Math.ceil(textWidth);
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (attachedTextView == null) {
            return;
        }
        Layout layout = attachedTextView.getLayout();
        if (layout == null) {
            return;
        }

        int scrollY = attachedTextView.getScrollY();
        int paddingTop = attachedTextView.getCompoundPaddingTop();
        int paddingBottom = attachedTextView.getCompoundPaddingBottom();
        int firstVisibleLine = layout.getLineForVertical(scrollY + paddingTop);
        int lastVisibleLine =
                layout.getLineForVertical(scrollY + attachedTextView.getHeight() - paddingBottom);
        float x = getWidth() - getPaddingRight();

        for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
            String lineNumberText = String.valueOf(line + 1);
            float baseline = paddingTop + layout.getLineBaseline(line) - scrollY;
            canvas.drawText(lineNumberText, x, baseline, lineNumberPaint);
        }
    }
}
