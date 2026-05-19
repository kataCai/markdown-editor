package com.shuzijun.markdown.editor.sync;

import org.jetbrains.annotations.NotNull;

/**
 * 源码编辑器视口联动辅助工具。
 * 该工具负责把 IntelliJ 编辑器原始事件上下文归一化为“应同步给预览页的语义锚点”，
 * 从而避免在不同事件来源下都机械复用 caret 行，导致滚动同步抖动或锚点偏移。
 * 当前规则遵循参考实现中的语义：
 * 1. 光标事件优先使用 caret 行。
 * 2. 滚动事件在文档中段优先使用视口中线行。
 * 3. 接近文档顶部/底部时分别优先对齐顶部/底部行，减少边缘区域回跳。
 */
public final class EditorViewportSyncSupport {

    /**
     * 顶部边界阈值。
     * 当视口顶部距离文档开头不足该阈值时，滚动同步优先按顶部行对齐。
     */
    private static final int EDGE_LINE_THRESHOLD = 3;

    private EditorViewportSyncSupport() {
    }

    /**
     * 根据事件来源和当前视口信息解析本次应同步的语义锚点。
     *
     * @param reason        触发原因，例如 caret、scroll、activation
     * @param caretLine     当前光标逻辑行
     * @param topLine       当前视口顶部逻辑行
     * @param bottomLine    当前视口底部逻辑行
     * @param middleLine    当前视口中线逻辑行
     * @param documentLines 文档总行数
     * @return 本次同步应使用的语义锚点
     */
    @NotNull
    public static ViewportAnchor resolveAnchor(
            @NotNull String reason,
            int caretLine,
            int topLine,
            int bottomLine,
            int middleLine,
            int documentLines
    ) {
        int safeDocumentLines = Math.max(documentLines, 1);
        int lastLine = Math.max(0, safeDocumentLines - 1);
        if (!"scroll".equals(reason)) {
            return new ViewportAnchor(clampLine(caretLine, lastLine));
        }
        if (topLine <= EDGE_LINE_THRESHOLD) {
            return new ViewportAnchor(clampLine(topLine, lastLine));
        }
        if (bottomLine >= Math.max(0, lastLine - EDGE_LINE_THRESHOLD)) {
            return new ViewportAnchor(clampLine(bottomLine, lastLine));
        }
        return new ViewportAnchor(clampLine(middleLine, lastLine));
    }

    private static int clampLine(int line, int lastLine) {
        if (line < 0) {
            return 0;
        }
        return Math.min(line, lastLine);
    }

    /**
     * 视口语义锚点。
     * 当前仅保存目标逻辑行；若后续需要携带顶部/中线策略元信息，可继续在该对象上扩展。
     */
    public static final class ViewportAnchor {

        private final int line;

        /**
         * 创建一条视口语义锚点。
         *
         * @param line 本次联动应同步的目标逻辑行
         */
        public ViewportAnchor(int line) {
            this.line = line;
        }

        public int getLine() {
            return line;
        }
    }
}
