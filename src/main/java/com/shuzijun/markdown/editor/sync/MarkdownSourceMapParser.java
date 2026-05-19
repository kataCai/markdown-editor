package com.shuzijun.markdown.editor.sync;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Markdown 源码映射解析器。
 * 该解析器负责从 Markdown 原文中提取当前联动链路所需的最小块级/行级源码锚点信息，
 * 供宿主构建结构化同步负载并供预览页建立更稳定的 DOM 绑定。
 * 当前阶段重点解决两类映射：
 * 1. 普通块的 `startLine/endLine` 范围。
 * 2. 表格块内部的行级锚点，避免整表线性插值导致“预览 -> 源码”偏差过大。
 */
public final class MarkdownSourceMapParser {

    private MarkdownSourceMapParser() {
    }

    /**
     * 解析 Markdown 文本为顺序稳定的源码块映射列表。
     *
     * @param markdown Markdown 原文
     * @return 按源码顺序排列的块映射列表
     */
    @NotNull
    public static List<BlockMapping> parse(@NotNull String markdown) {
        String[] lines = markdown.split("\\r\\n|\\r|\\n", -1);
        List<BlockMapping> blocks = new ArrayList<>();
        int lineIndex = 0;
        while (lineIndex < lines.length) {
            if (isBlankLine(lines[lineIndex])) {
                lineIndex += 1;
                continue;
            }

            String fenceMarker = resolveFenceMarker(lines[lineIndex]);
            if (fenceMarker != null) {
                int startLine = lineIndex;
                lineIndex += 1;
                while (lineIndex < lines.length && !isFenceClose(lines[lineIndex], fenceMarker)) {
                    lineIndex += 1;
                }
                if (lineIndex < lines.length) {
                    lineIndex += 1;
                }
                blocks.add(new BlockMapping(BlockType.GENERIC, startLine, Math.max(startLine, lineIndex - 1), Collections.emptyList()));
                continue;
            }

            if (isHeadingLine(lines[lineIndex]) || isHorizontalRuleLine(lines[lineIndex])) {
                blocks.add(new BlockMapping(BlockType.GENERIC, lineIndex, lineIndex, Collections.emptyList()));
                lineIndex += 1;
                continue;
            }

            if (isTableStart(lines, lineIndex)) {
                int startLine = lineIndex;
                List<RowMapping> rows = new ArrayList<>();
                rows.add(new RowMapping(lineIndex, lineIndex));
                rows.add(new RowMapping(lineIndex + 1, lineIndex + 1));
                lineIndex += 2;
                while (lineIndex < lines.length && looksLikeTableRow(lines[lineIndex]) && !isBlankLine(lines[lineIndex])) {
                    rows.add(new RowMapping(lineIndex, lineIndex));
                    lineIndex += 1;
                }
                blocks.add(new BlockMapping(BlockType.TABLE, startLine, Math.max(startLine, lineIndex - 1), rows));
                continue;
            }

            if (isBlockquoteLine(lines[lineIndex])) {
                int startLine = lineIndex;
                lineIndex += 1;
                while (lineIndex < lines.length) {
                    if (isBlankLine(lines[lineIndex]) || !isBlockquoteLine(lines[lineIndex])) {
                        break;
                    }
                    lineIndex += 1;
                }
                blocks.add(new BlockMapping(BlockType.GENERIC, startLine, Math.max(startLine, lineIndex - 1), Collections.emptyList()));
                continue;
            }

            if (isListStartLine(lines[lineIndex])) {
                int startLine = lineIndex;
                lineIndex += 1;
                while (lineIndex < lines.length) {
                    if (isBlankLine(lines[lineIndex])) {
                        break;
                    }
                    if (isListStartLine(lines[lineIndex]) || isIndentedContinuation(lines[lineIndex])) {
                        lineIndex += 1;
                        continue;
                    }
                    break;
                }
                blocks.add(new BlockMapping(BlockType.GENERIC, startLine, Math.max(startLine, lineIndex - 1), Collections.emptyList()));
                continue;
            }

            int startLine = lineIndex;
            lineIndex += 1;
            while (lineIndex < lines.length && !isBlankLine(lines[lineIndex])) {
                if (resolveFenceMarker(lines[lineIndex]) != null
                        || isHeadingLine(lines[lineIndex])
                        || isHorizontalRuleLine(lines[lineIndex])
                        || isBlockquoteLine(lines[lineIndex])
                        || isListStartLine(lines[lineIndex])
                        || isTableStart(lines, lineIndex)) {
                    break;
                }
                lineIndex += 1;
            }
            blocks.add(new BlockMapping(BlockType.GENERIC, startLine, Math.max(startLine, lineIndex - 1), Collections.emptyList()));
        }
        return blocks;
    }

    private static boolean isBlankLine(@NotNull String line) {
        return line.trim().isEmpty();
    }

    private static String resolveFenceMarker(@NotNull String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("```")) {
            return "```";
        }
        if (trimmed.startsWith("~~~")) {
            return "~~~";
        }
        return null;
    }

    private static boolean isFenceClose(@NotNull String line, @NotNull String marker) {
        return line.trim().startsWith(marker);
    }

    private static boolean isHeadingLine(@NotNull String line) {
        return line.matches("^ {0,3}#{1,6}\\s+.*$");
    }

    private static boolean isHorizontalRuleLine(@NotNull String line) {
        return line.matches("^ {0,3}(?:([-*_])\\s*){3,}$");
    }

    private static boolean isTableStart(@NotNull String[] lines, int lineIndex) {
        return lineIndex + 1 < lines.length
                && looksLikeTableHeader(lines[lineIndex])
                && isTableDelimiterLine(lines[lineIndex + 1]);
    }

    private static boolean looksLikeTableHeader(@NotNull String line) {
        return line.contains("|");
    }

    private static boolean isTableDelimiterLine(@NotNull String line) {
        return line.matches("^\\s*\\|?(?:\\s*:?-+:?\\s*\\|)+\\s*:?-+:?\\s*\\|?\\s*$");
    }

    private static boolean looksLikeTableRow(@NotNull String line) {
        return line.contains("|");
    }

    private static boolean isBlockquoteLine(@NotNull String line) {
        return line.matches("^ {0,3}> ?.*$");
    }

    private static boolean isListStartLine(@NotNull String line) {
        return line.matches("^ {0,3}(?:[*+-]|\\d+[.)])\\s+.*$");
    }

    private static boolean isIndentedContinuation(@NotNull String line) {
        return line.matches("^ {2,}\\S.*$");
    }

    /**
     * 源码块类型。
     */
    public enum BlockType {
        GENERIC,
        TABLE
    }

    /**
     * Markdown 源码块映射。
     */
    public static final class BlockMapping {

        private final BlockType blockType;
        private final int startLine;
        private final int endLine;
        private final List<RowMapping> rowMappings;

        /**
         * 创建一条 Markdown 源码块映射。
         *
         * @param blockType   块类型
         * @param startLine   起始行
         * @param endLine     结束行
         * @param rowMappings 表格场景下的行级锚点；其他块为空列表
         */
        public BlockMapping(@NotNull BlockType blockType,
                            int startLine,
                            int endLine,
                            @NotNull List<RowMapping> rowMappings) {
            this.blockType = blockType;
            this.startLine = startLine;
            this.endLine = endLine;
            this.rowMappings = Collections.unmodifiableList(new ArrayList<>(rowMappings));
        }

        @NotNull
        public BlockType getBlockType() {
            return blockType;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        @NotNull
        public List<RowMapping> getRowMappings() {
            return rowMappings;
        }
    }

    /**
     * 表格内部的源码行映射。
     */
    public static final class RowMapping {

        private final int startLine;
        private final int endLine;

        /**
         * 创建一条表格行级源码映射。
         *
         * @param startLine 行起始位置
         * @param endLine   行结束位置
         */
        public RowMapping(int startLine, int endLine) {
            this.startLine = startLine;
            this.endLine = endLine;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }
    }
}
