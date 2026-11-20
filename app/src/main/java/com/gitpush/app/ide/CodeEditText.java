package com.gitpush.app.ide;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.EditText;

import com.gitpush.app.R;
import com.gitpush.app.ui.Rich;
import com.gitpush.app.ui.Ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Fast code editor: viewport-only syntax highlighting, fixed line-number gutter, red error lines, pinch-to-zoom,
 * auto-indent and an undo/redo history (per tab).
 */
public class CodeEditText extends EditText {
    public interface Listener { void onZoom(float sp); void onCursor(int line, int col); void onEdited(); }

    static final class HlColor extends ForegroundColorSpan { HlColor(int c) { super(c); } }
    static final class HlStyle extends StyleSpan { HlStyle(int s) { super(s); } }

    public static final class History {
        static final class Edit { int start; String removed, added; long time; }
        final ArrayList<Edit> undo = new ArrayList<>(), redo = new ArrayList<>();
    }

    private Listener listener;
    private final Paint gutterBg = new Paint(), numPaint = new Paint(Paint.ANTI_ALIAS_FLAG), divider = new Paint(), errBg = new Paint(), curBg = new Paint();
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable hl = this::highlight;
    private final ScaleGestureDetector scale;
    private final Set<Integer> errorLines = new HashSet<>();
    private History history = new History();
    private boolean applying, lineNumbers = true, colorSyntax, autoIndent = true, wrap;
    private int gutterW, tabSize = 4, textColor, mutedColor, errColor = 0xFFE5484D;
    private float sizeSp = 12f;
    private String ext = "", pendingRemoved = "";
    private Rich.CodeColors palette;

    public CodeEditText(Context c) {
        super(c);
        setBackground(null);
        setSaveEnabled(false);
        setTypeface(Typeface.MONOSPACE);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setHorizontallyScrolling(true);
        setVerticalScrollBarEnabled(true);
        setHorizontalScrollBarEnabled(true);
        setLineSpacing(0, 1.12f);
        textColor = Ui.color(c, R.attr.gpText);
        mutedColor = Ui.color(c, R.attr.gpMuted);
        setTextColor(textColor);
        setHighlightColor(0x55888888);
        gutterBg.setColor(Ui.color(c, R.attr.gpSurface));
        divider.setColor(Ui.color(c, R.attr.gpOutline));
        numPaint.setColor(mutedColor);
        numPaint.setTypeface(Typeface.MONOSPACE);
        numPaint.setTextAlign(Paint.Align.RIGHT);
        errBg.setColor(0x22E5484D);
        curBg.setColor(Ui.color(c, R.attr.gpAuroraA));
        palette = Rich.codeColors(c);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        scale = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) { setFontSize(sizeSp * d.getScaleFactor()); if (listener != null) listener.onZoom(sizeSp); return true; }
        });
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!applying) pendingRemoved = s.subSequence(start, start + count).toString();
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (applying) return;
                record(start, pendingRemoved, s.subSequence(start, start + count).toString());
            }
            @Override public void afterTextChanged(Editable e) {
                updateGutter();
                scheduleHighlight(150);
                if (!applying && listener != null) listener.onEdited();
            }
        });
        setFilters(new InputFilter[]{(source, start, end, dest, dstart, dend) -> {
            if (!autoIndent || applying || end - start != 1 || source.charAt(start) != '\n') return null;
            int ls = dstart;
            while (ls > 0 && dest.charAt(ls - 1) != '\n') ls--;
            StringBuilder ind = new StringBuilder();
            for (int i = ls; i < dstart && (dest.charAt(i) == ' ' || dest.charAt(i) == '\t'); i++) ind.append(dest.charAt(i));
            int p = dstart - 1;
            while (p >= ls && dest.charAt(p) == ' ') p--;
            if (p >= ls) { char ch = dest.charAt(p); if (ch == '{' || ch == '(' || ch == '[' || ch == ':') ind.append(tabString()); }
            return "\n" + ind;
        }});
    }

    // ---------------------------------------------------------------- configuration
    public void setListener(Listener l) { listener = l; }
    public void setFontSize(float sp) { sizeSp = Math.max(8f, Math.min(28f, sp)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp); updateGutter(); scheduleHighlight(60); }
    public float fontSize() { return sizeSp; }
    public void setLineNumbers(boolean on) { lineNumbers = on; updateGutter(); invalidate(); }
    public void setColorSyntax(boolean on) { colorSyntax = on; scheduleHighlight(0); }
    public void setAutoIndent(boolean on) { autoIndent = on; }
    public void setTabSize(int n) { tabSize = n; }
    public void setWrap(boolean on) { wrap = on; setHorizontallyScrolling(!on); requestLayout(); invalidate(); }
    public void setExt(String e) { ext = e == null ? "" : e; scheduleHighlight(0); }
    public void setErrorLines(Set<Integer> lines) { errorLines.clear(); errorLines.addAll(lines); invalidate(); }
    public String tabString() { StringBuilder sb = new StringBuilder(); for (int i = 0; i < tabSize; i++) sb.append(' '); return sb.toString(); }

    public void loadText(String text) {
        applying = true;
        setText(text, BufferType.EDITABLE);
        applying = false;
        updateGutter();
        scheduleHighlight(0);
    }

    public History getHistory() { return history; }
    public void setHistory(History hst) { history = hst == null ? new History() : hst; }

    public void insertAtCursor(String s) {
        int a = Math.max(0, getSelectionStart()), b = Math.max(0, getSelectionEnd());
        getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    // ---------------------------------------------------------------- undo / redo
    private void record(int start, String removed, String added) {
        List<History.Edit> u = history.undo;
        long now = System.currentTimeMillis();
        if (!u.isEmpty()) {
            History.Edit last = u.get(u.size() - 1);
            if (removed.isEmpty() && last.removed.isEmpty() && added.length() == 1 && !added.equals("\n") && last.start + last.added.length() == start
                    && now - last.time < 1000 && last.added.length() < 40) { last.added += added; last.time = now; history.redo.clear(); return; }
        }
        History.Edit e = new History.Edit();
        e.start = start; e.removed = removed; e.added = added; e.time = now;
        u.add(e);
        if (u.size() > 600) u.remove(0);
        history.redo.clear();
    }

    public boolean canUndo() { return !history.undo.isEmpty(); }
    public boolean canRedo() { return !history.redo.isEmpty(); }

    public void undo() {
        if (history.undo.isEmpty()) return;
        History.Edit e = history.undo.remove(history.undo.size() - 1);
        apply(e.start, e.added.length(), e.removed);
        history.redo.add(e);
    }

    public void redo() {
        if (history.redo.isEmpty()) return;
        History.Edit e = history.redo.remove(history.redo.size() - 1);
        apply(e.start, e.removed.length(), e.added);
        history.undo.add(e);
    }

    private void apply(int start, int removeLen, String insert) {
        applying = true;
        Editable t = getText();
        int s = Math.min(start, t.length());
        t.replace(s, Math.min(t.length(), s + removeLen), insert);
        setSelection(Math.min(t.length(), s + insert.length()));
        applying = false;
        if (listener != null) listener.onEdited();
    }

    // ---------------------------------------------------------------- highlight (visible lines only)
    private void scheduleHighlight(long delay) { h.removeCallbacks(hl); h.postDelayed(hl, delay); }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) { super.onScrollChanged(l, t, ol, ot); scheduleHighlight(40); }

    private void highlight() {
        Layout lay = getLayout();
        Editable e = getText();
        if (lay == null || e.length() == 0) return;
        int first = Math.max(0, lay.getLineForVertical(getScrollY()) - 30);
        int last = Math.min(lay.getLineCount() - 1, lay.getLineForVertical(getScrollY() + getHeight()) + 30);
        int s = lay.getLineStart(first), en = Math.min(e.length(), lay.getLineEnd(last));
        for (HlColor o : e.getSpans(s, en, HlColor.class)) e.removeSpan(o);
        for (HlStyle o : e.getSpans(s, en, HlStyle.class)) e.removeSpan(o);
        if (ext.isEmpty() || en <= s) return;
        boolean markup = Rich.MARKUP_EXT.contains(ext), hash = Rich.HASH_EXT.contains(ext);
        String chunk = e.subSequence(s, en).toString();
        Matcher m = Rich.TOKEN.matcher(chunk);
        while (m.find()) {
            String t = m.group();
            Integer col = null; boolean bold = false, italic = false;
            if (t.startsWith("//")) { col = colorSyntax ? palette.comment : mutedColor; italic = true; }
            else if (t.startsWith("#")) { if (hash) { col = colorSyntax ? palette.comment : mutedColor; italic = true; } }
            else if (t.startsWith("/*") || t.startsWith("<!--")) { col = colorSyntax ? palette.comment : mutedColor; italic = true; }
            else if (t.startsWith("\"") || t.startsWith("'")) col = colorSyntax ? palette.string : mutedColor;
            else if (t.startsWith("<")) { if (markup) { col = colorSyntax ? palette.tag : textColor; bold = true; } }
            else if (Character.isDigit(t.charAt(0))) col = colorSyntax ? palette.number : textColor;
            else if (!markup && Rich.KEYWORDS.contains(t)) { col = colorSyntax ? palette.keyword : textColor; bold = true; }
            if (col != null) {
                e.setSpan(new HlColor(col), s + m.start(), s + m.end(), Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (bold || italic) e.setSpan(new HlStyle(bold ? Typeface.BOLD : Typeface.ITALIC), s + m.start(), s + m.end(), Editable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    // ---------------------------------------------------------------- gutter
    private void updateGutter() {
        numPaint.setTextSize(getTextSize() * 0.82f);
        int lines = Math.max(1, getLineCount());
        int digits = String.valueOf(lines).length();
        int w = lineNumbers ? (int) (numPaint.measureText("0") * (digits + 1)) + Ui.dp(getContext(), 10) : 0;
        if (w != gutterW || getPaddingLeft() != w + Ui.dp(getContext(), 8)) {
            gutterW = w;
            setPadding(w + Ui.dp(getContext(), 8), Ui.dp(getContext(), 6), Ui.dp(getContext(), 12), Ui.dp(getContext(), 80));
        }
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        Layout lay = getLayout();
        if (lay != null && listener != null && selStart >= 0 && selStart <= getText().length()) {
            int line = lay.getLineForOffset(selStart);
            listener.onCursor(line + 1, selStart - lay.getLineStart(line) + 1);
        }
        invalidate();
    }

    private int cacheOff = -1, cacheCnt, cacheLen = -1;

    private int logicalLine(int offset) {
        CharSequence t = getText();
        if (cacheLen != t.length() || offset < cacheOff) { cacheOff = 0; cacheCnt = 0; cacheLen = t.length(); }
        for (int i = cacheOff; i < offset && i < t.length(); i++) if (t.charAt(i) == '\n') cacheCnt++;
        cacheOff = offset;
        return cacheCnt;
    }

    @Override protected void onDraw(Canvas c) {
        Layout lay = getLayout();
        if (lay != null) {
            int top = getCompoundPaddingTop();
            int sel = getSelectionStart();
            if (sel >= 0 && sel <= getText().length()) {
                int cl = lay.getLineForOffset(sel);
                c.drawRect(getScrollX() + gutterW, top + lay.getLineTop(cl), getScrollX() + getWidth(), top + lay.getLineBottom(cl), curBg);
            }
            if (!errorLines.isEmpty()) {
                int first = lay.getLineForVertical(getScrollY()), last = lay.getLineForVertical(getScrollY() + getHeight());
                for (int i = first; i <= last; i++) {
                    int logical = wrap ? logicalLine(lay.getLineStart(i)) + 1 : i + 1;
                    if (errorLines.contains(logical)) c.drawRect(getScrollX(), top + lay.getLineTop(i), getScrollX() + getWidth(), top + lay.getLineBottom(i), errBg);
                }
            }
        }
        super.onDraw(c);
        if (lay != null && lineNumbers) {
            int top = getCompoundPaddingTop();
            float x0 = getScrollX();
            c.drawRect(x0, getScrollY(), x0 + gutterW, getScrollY() + getHeight(), gutterBg);
            c.drawLine(x0 + gutterW, getScrollY(), x0 + gutterW, getScrollY() + getHeight(), divider);
            int first = lay.getLineForVertical(getScrollY()), last = lay.getLineForVertical(getScrollY() + getHeight());
            CharSequence t = getText();
            cacheOff = -1;
            for (int i = first; i <= last; i++) {
                int start = lay.getLineStart(i);
                boolean startsPara = i == 0 || (start > 0 && start <= t.length() && t.charAt(start - 1) == '\n');
                if (!startsPara) continue;
                int logical = wrap ? logicalLine(start) + 1 : i + 1;
                numPaint.setColor(errorLines.contains(logical) ? errColor : mutedColor);
                c.drawText(String.valueOf(logical), x0 + gutterW - Ui.dp(getContext(), 6), top + lay.getLineBaseline(i), numPaint);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        scale.onTouchEvent(e);
        if (scale.isInProgress() || e.getPointerCount() > 1) return true;
        return super.onTouchEvent(e);
    }
}
