package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

/**
 * 预览表格窗口光标的种类映射。
 * 页面计算值还是普通箭头时不换成拖拽光标；种类为空时恢复系统箭头。
 */
public class PreviewTableResizeCursorTest {

    /**
     * 带位图地址的列宽计算值。
     */
    private static final String COLUMN_COMPUTED = "url(\"cursor.png\") 16 16, col-resize";

    /**
     * 行高关键字计算值。
     */
    private static final String ROW_COMPUTED = "row-resize";

    /**
     * 系统箭头计算值。
     */
    private static final String DEFAULT_COMPUTED = "default";

    /**
     * 验证列宽、行高、空种类和普通箭头四种映射。
     */
    @Test
    public void shouldMapResizeCursorFromKindAndComputedStyle() {
        Assert.assertEquals("列宽且计算值已是拖拽光标时使用列宽光标",
                PreviewTableResizeCursor.KIND_COLUMN,
                PreviewTableResizeCursor.resolveAction(PreviewTableResizeCursor.KIND_COLUMN, COLUMN_COMPUTED));
        Assert.assertEquals("行高且计算值已是拖拽光标时使用行高光标",
                PreviewTableResizeCursor.KIND_ROW,
                PreviewTableResizeCursor.resolveAction(PreviewTableResizeCursor.KIND_ROW, ROW_COMPUTED));
        Assert.assertEquals("种类为空时恢复系统箭头",
                "",
                PreviewTableResizeCursor.resolveAction("", COLUMN_COMPUTED));
        Assert.assertEquals("计算值仍是普通箭头时不更换窗口光标",
                PreviewTableResizeCursor.ACTION_UNCHANGED,
                PreviewTableResizeCursor.resolveAction(PreviewTableResizeCursor.KIND_COLUMN, DEFAULT_COMPUTED));
    }
}
