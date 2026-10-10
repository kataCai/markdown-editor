package com.shuzijun.markdown.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;

/**
 * 把预览表格的列宽、行高光标落到 IDE 窗口上。
 * remote JCEF 不会把页面里的 cursor 交给当前窗口，所以在页面计算值已经是拖拽光标时，改用同一张 PNG。
 */
public final class PreviewTableResizeCursor {

    /**
     * 列宽拖拽。
     */
    public static final String KIND_COLUMN = "column";
    /**
     * 行高拖拽。
     */
    public static final String KIND_ROW = "row";
    /**
     * 页面计算值还不是拖拽光标时保持原样。
     */
    public static final String ACTION_UNCHANGED = "unchanged";

    private static final String COLUMN_CURSOR_RESOURCE = "/template/cursor/col-resize.png";
    private static final String ROW_CURSOR_RESOURCE = "/template/cursor/row-resize.png";
    private static final String COMPUTED_COLUMN = "col-resize";
    private static final String COMPUTED_ROW = "row-resize";
    private static final String COMPUTED_URL = "url(";
    private static final int HOTSPOT = 16;
    private static final String COLUMN_CURSOR_NAME = "PreviewTableColumnResize";
    private static final String ROW_CURSOR_NAME = "PreviewTableRowResize";

    private static Cursor columnCursor;
    private static Cursor rowCursor;

    private PreviewTableResizeCursor() {
    }

    /**
     * 决定这次上报要换成哪一种窗口光标。
     * 种类为空时恢复系统箭头。种类有值但计算值仍是普通箭头时不改当前光标。
     *
     * @param kind            页面上报的 column、row 或空字符串
     * @param computedCursor  指针下面节点的 CSS cursor 计算值
     * @return 列宽、行高、空字符串（系统箭头）或 {@link #ACTION_UNCHANGED}
     */
    @NotNull
    public static String resolveAction(@Nullable String kind, @Nullable String computedCursor) {
        if (kind == null || kind.isEmpty()) {
            return "";
        }
        if (!acceptsComputedCursor(computedCursor)) {
            return ACTION_UNCHANGED;
        }
        if (KIND_COLUMN.equals(kind) || KIND_ROW.equals(kind)) {
            return kind;
        }
        return "";
    }

    /**
     * 页面计算值已经表达列宽、行高或位图光标时才接受。
     *
     * @param computedCursor CSS cursor 计算值
     * @return 可以据此更换窗口光标时返回 true
     */
    public static boolean acceptsComputedCursor(@Nullable String computedCursor) {
        if (computedCursor == null || computedCursor.isEmpty()) {
            return false;
        }
        return computedCursor.contains(COMPUTED_COLUMN)
                || computedCursor.contains(COMPUTED_ROW)
                || computedCursor.contains(COMPUTED_URL);
    }

    /**
     * 按解析结果设置组件光标。组件不存在时直接返回。
     *
     * @param component       预览浏览器组件
     * @param kind            页面上报的种类
     * @param computedCursor  CSS cursor 计算值
     */
    public static void apply(@Nullable JComponent component, @Nullable String kind, @Nullable String computedCursor) {
        if (component == null) {
            return;
        }
        String action = resolveAction(kind, computedCursor);
        if (ACTION_UNCHANGED.equals(action)) {
            return;
        }
        if (action.isEmpty()) {
            component.setCursor(Cursor.getDefaultCursor());
            return;
        }
        Cursor cursor = cursorFor(action);
        if (cursor != null) {
            component.setCursor(cursor);
        }
    }

    @Nullable
    private static Cursor cursorFor(@NotNull String action) {
        if (KIND_COLUMN.equals(action)) {
            columnCursor = loadCursor(columnCursor, COLUMN_CURSOR_RESOURCE, COLUMN_CURSOR_NAME);
            return columnCursor;
        }
        rowCursor = loadCursor(rowCursor, ROW_CURSOR_RESOURCE, ROW_CURSOR_NAME);
        return rowCursor;
    }

    @Nullable
    private static Cursor loadCursor(@Nullable Cursor current, @NotNull String resource, @NotNull String name) {
        if (current != null) {
            return current;
        }
        try (InputStream inputStream = PreviewTableResizeCursor.class.getResourceAsStream(resource)) {
            if (inputStream == null) {
                return null;
            }
            Image image = ImageIO.read(inputStream);
            if (image == null) {
                return null;
            }
            return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(HOTSPOT, HOTSPOT), name);
        } catch (IOException ex) {
            return null;
        }
    }
}
