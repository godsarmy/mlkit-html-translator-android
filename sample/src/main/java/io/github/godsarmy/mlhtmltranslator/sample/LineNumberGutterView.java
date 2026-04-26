package io.github.godsarmy.mlhtmltranslator.sample;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import androidx.annotation.Nullable;

public final class LineNumberGutterView extends View {
    private static final int DEFAULT_TEXT_COLOR = 0xFF757575;
    private final Paint lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Nullable private EditText attachedEditText;

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

    public void bindTo(EditText editText) {
        attachedEditText = editText;
        applyTextMetricsFrom(editText);
        requestLayout();
        invalidate();
    }

    public void applyTextMetricsFrom(EditText contentView) {
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int lineCount = 1;
        if (attachedEditText != null) {
            lineCount = Math.max(1, attachedEditText.getLineCount());
        }
        int digits = String.valueOf(lineCount).length();
        float textWidth = lineNumberPaint.measureText("8".repeat(Math.max(1, digits)));
        int desiredWidth = getPaddingLeft() + getPaddingRight() + (int) Math.ceil(textWidth);
        int measuredWidth = resolveSize(desiredWidth, widthMeasureSpec);
        int measuredHeight = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (attachedEditText == null) {
            return;
        }
        Layout layout = attachedEditText.getLayout();
        if (layout == null) {
            return;
        }

        int scrollY = attachedEditText.getScrollY();
        int paddingTop = attachedEditText.getCompoundPaddingTop();
        int paddingBottom = attachedEditText.getCompoundPaddingBottom();
        int firstVisibleLine = layout.getLineForVertical(scrollY + paddingTop);
        int lastVisibleLine =
                layout.getLineForVertical(scrollY + attachedEditText.getHeight() - paddingBottom);
        float x = getWidth() - getPaddingRight();

        for (int line = firstVisibleLine; line <= lastVisibleLine; line++) {
            String lineNumberText = String.valueOf(line + 1);
            float baseline = paddingTop + layout.getLineBaseline(line) - scrollY;
            canvas.drawText(lineNumberText, x, baseline, lineNumberPaint);
        }
    }
}
