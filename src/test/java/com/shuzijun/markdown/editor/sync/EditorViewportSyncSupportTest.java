package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 源码编辑器视口锚点解析测试。
 * 这些测试用于固定“光标事件”和“可视区滚动事件”在宿主侧应如何选择语义锚点，
 * 避免后续再次退化为所有事件都直接使用 caret 行，导致预览滚动抖动或 `sv` 模式跟随错误位置。
 */
public class EditorViewportSyncSupportTest {

    /**
     * 验证光标事件始终优先使用当前 caret 行。
     * 这是用户显式移动光标时最符合预期的联动行为，不应被可视区锚点覆盖。
     */
    @Test
    public void shouldUseCaretLineForCaretReason() {
        EditorViewportSyncSupport.ViewportAnchor anchor = EditorViewportSyncSupport.resolveAnchor(
                "caret",
                18,
                10,
                30,
                20,
                100
        );

        Assert.assertEquals(18, anchor.getLine());
    }

    /**
     * 验证滚动事件在文档中段时优先使用视口中线语义行，而不是继续错误复用 caret 行。
     * 这能避免“光标停在旧位置，但用户已经滚动到新位置”时，预览仍围绕旧 caret 抖动。
     */
    @Test
    public void shouldUseViewportMiddleLineForScrollInDocumentMiddle() {
        EditorViewportSyncSupport.ViewportAnchor anchor = EditorViewportSyncSupport.resolveAnchor(
                "scroll",
                8,
                100,
                140,
                120,
                300
        );

        Assert.assertEquals(120, anchor.getLine());
    }

    /**
     * 验证滚动事件在文档顶部附近优先对齐顶部行。
     * 这样可以避免顶部区域因为中线锚点偏下而造成预览初始同步看起来“总是往下跳”。
     */
    @Test
    public void shouldPreferTopLineNearDocumentStart() {
        EditorViewportSyncSupport.ViewportAnchor anchor = EditorViewportSyncSupport.resolveAnchor(
                "scroll",
                12,
                1,
                20,
                10,
                300
        );

        Assert.assertEquals(1, anchor.getLine());
    }

    /**
     * 验证滚动事件在文档底部附近优先对齐底部行。
     * 这样可以减少长文档接近末尾时的回跳，并让预览更自然地停留在结尾附近。
     */
    @Test
    public void shouldPreferBottomLineNearDocumentEnd() {
        EditorViewportSyncSupport.ViewportAnchor anchor = EditorViewportSyncSupport.resolveAnchor(
                "scroll",
                250,
                280,
                299,
                289,
                300
        );

        Assert.assertEquals(299, anchor.getLine());
    }
}
