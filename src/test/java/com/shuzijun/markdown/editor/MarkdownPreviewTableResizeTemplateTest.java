package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 预览表格列宽行高的模板契约。
 * 这些断言只读 {@code default.html}，确认尺寸样式、拖拽入口和渲染后重放还在，
 * 并且没有把列宽写进表格节点。
 */
public class MarkdownPreviewTableResizeTemplateTest {

    /**
     * 预览页模板路径。
     */
    private static final Path DEFAULT_HTML_PATH = Path.of("src/main/resources/template/default.html");

    /**
     * 渲染后重放入口。定义和两处调用都带这个前缀。
     */
    private static final String REFRESH_CALL = "schedulePreviewTableResizeRefresh(";

    /**
     * 即时渲染里的表格表面。
     */
    private static final String IR_TABLE_SURFACE = ".vditor-ir pre.vditor-reset";

    /**
     * 所见即所得里的表格表面。
     */
    private static final String WYSIWYG_TABLE_SURFACE = ".vditor-wysiwyg pre.vditor-reset";

    /**
     * 工具栏预览阅读区里的表格表面。
     */
    private static final String PREVIEW_TABLE_SURFACE = ".vditor-preview .vditor-reset";

    /**
     * 列宽光标图。左右箭头加竖向分割线。
     */
    private static final Path COLUMN_RESIZE_CURSOR_PATH = Path.of("src/main/resources/template/cursor/col-resize.png");

    /**
     * 行高光标图。上下箭头加横向分割线。
     */
    private static final Path ROW_RESIZE_CURSOR_PATH = Path.of("src/main/resources/template/cursor/row-resize.png");

    /**
     * 标准拖拽光标的边长。热点落在中心 16,16。
     */
    private static final int RESIZE_CURSOR_SIZE = 32;

    /**
     * 验证尺寸记录和样式重放留在 head，不进入表格节点。
     *
     * @throws IOException 模板读取失败时抛出
     */
    @Test
    public void shouldKeepPreviewTableSizeRulesOutOfTheTableDom() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String rowCss = sliceBetween(defaultHtml, "function appendPreviewTableRowCss", "function appendPreviewTableRules");

        Assert.assertTrue("应保留尺寸状态",
                defaultHtml.contains("const previewTableResizeState"));
        Assert.assertTrue("应保留尺寸键",
                defaultHtml.contains("function resolvePreviewTableSizeKey("));
        Assert.assertTrue("应保留结构选择器",
                defaultHtml.contains("function buildPreviewTableStructuralSelector("));
        Assert.assertTrue("应保留样式重放",
                defaultHtml.contains("function applyPreviewTableSizeStyles("));
        Assert.assertTrue("尺寸样式应写到 head 里的固定节点",
                defaultHtml.contains("markdown-preview-table-sizes")
                        && defaultHtml.contains("document.head.appendChild(styleElement)"));
        Assert.assertTrue("拖过列宽的表应使用固定布局和边框盒",
                defaultHtml.contains("table-layout: fixed")
                        && defaultHtml.contains("box-sizing: border-box")
                        && defaultHtml.contains("white-space: normal"));
        Assert.assertTrue("行高只写 height 和 min-height",
                rowCss.contains("min-height: ")
                        && !rowCss.contains("overflow"));
        Assert.assertTrue("合并单元格、内层表和源码区应被跳过",
                defaultHtml.contains("function isPreviewTableResizable(")
                        && defaultHtml.contains("function previewTableCellIsMerged(")
                        && defaultHtml.contains(".vditor-sv"));
        Assert.assertFalse("模板不应创建列分组节点",
                defaultHtml.contains("colgroup"));
        Assert.assertFalse("模板不应创建列元素",
                defaultHtml.contains("createElement(\"col\")"));
    }

    /**
     * 验证编辑器就绪后绑定列宽和行高拖拽。
     *
     * @throws IOException 模板读取失败时抛出
     */
    @Test
    public void shouldBindPreviewTableColumnAndRowResize() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String afterHook = sliceBetween(defaultHtml, "after() {", "blur(md)");

        Assert.assertTrue("after 回调应绑定表格拖拽",
                afterHook.contains("bindPreviewTableResize();"));
        Assert.assertTrue("应保留拖拽绑定入口",
                defaultHtml.contains("function bindPreviewTableResize("));
        Assert.assertTrue("竖边热区为 12px，列宽下限为 36px",
                defaultHtml.contains("const PREVIEW_TABLE_RESIZE_HIT_PX = 12;")
                        && defaultHtml.contains("const PREVIEW_TABLE_COLUMN_MIN_PX = 36;"));
        Assert.assertTrue("贴边滚动为 24px 热区、每帧 16px",
                defaultHtml.contains("const PREVIEW_TABLE_EDGE_SCROLL_PX = 24;")
                        && defaultHtml.contains("const PREVIEW_TABLE_EDGE_SCROLL_STEP_PX = 16;"));
        Assert.assertTrue("光标规则挂在 html 上",
                defaultHtml.contains("html.markdown-preview-table-col-resize")
                        && defaultHtml.contains("html.markdown-preview-table-row-resize")
                        && defaultHtml.contains("document.documentElement"));
        Assert.assertTrue("光标图按标准关键字和中心热点引用",
                defaultHtml.contains("{{service}}resources/template/cursor/col-resize.png")
                        && defaultHtml.contains("{{service}}resources/template/cursor/row-resize.png")
                        && defaultHtml.contains("16 16, col-resize")
                        && defaultHtml.contains("16 16, row-resize"));
        Assert.assertTrue("拖拽遮罩按列和行区分光标",
                defaultHtml.contains("markdown-preview-table-resize-shield")
                        && defaultHtml.contains("data-resize-kind=\"column\"")
                        && defaultHtml.contains("data-resize-kind=\"row\""));
        Assert.assertTrue("悬停格使用内联 important 光标，遮罩挂在 body 上",
                defaultHtml.contains("setProperty(\"cursor\"")
                        && defaultHtml.contains("document.body.appendChild(shield)")
                        && defaultHtml.contains("function clearPreviewTableCellCursor("));
        Assert.assertTrue("页面把光标计算值回传宿主",
                defaultHtml.contains("emitPreviewSyncMessage(\"previewCursorTrace\"")
                        && defaultHtml.contains("computedCursor: readPreviewCursorComputed(clientX, clientY)"));
        Assert.assertTrue("左缘拖前一列，上缘拖上一行",
                defaultHtml.contains("cell.cellIndex - 1")
                        && defaultHtml.contains("row.rowIndex - 1")
                        && defaultHtml.contains("document.elementFromPoint("));
        Assert.assertTrue("指示线对齐共用边并加粗到 2px",
                defaultHtml.contains("markdown-preview-table-resize-guide")
                        && defaultHtml.contains("position: fixed;")
                        && defaultHtml.contains("cellRect.right + \"px\"")
                        && defaultHtml.contains("guide.style.height = \"2px\""));
        Assert.assertTrue("行高按下前应先量内容高度",
                defaultHtml.contains("function measurePreviewTableRowFloor("));
        Assert.assertTrue("双击应能分别清除列宽和行高",
                defaultHtml.contains("function clearPreviewTableColumnWidths(")
                        && defaultHtml.contains("function clearPreviewTableRowHeight("));
    }

    /**
     * 验证表格 DOM 被换掉之后，三处表面按同一套记录重放。
     *
     * @throws IOException 模板读取失败时抛出
     */
    @Test
    public void shouldReplayPreviewTableSizesAfterRender() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String rebuild = sliceBetween(defaultHtml,
                "function schedulePreviewSyncArtifactsRebuild",
                "function resolveScrollTopForLine");
        String observer = sliceBetween(defaultHtml, "new MutationObserver", "mutationObserver.observe");
        int refreshCallCount = countOccurrences(defaultHtml, REFRESH_CALL);

        Assert.assertTrue("应保留渲染后重放入口",
                defaultHtml.contains("function schedulePreviewTableResizeRefresh(")
                        && defaultHtml.contains("function refreshPreviewTableResize("));
        Assert.assertTrue("源码映射重建末尾应重放表格尺寸",
                rebuild.contains(REFRESH_CALL));
        Assert.assertTrue("预览 DOM 变化后也应重放表格尺寸",
                observer.contains(REFRESH_CALL));
        Assert.assertTrue("重放入口至少包含定义和两处调用",
                refreshCallCount >= 3);
        Assert.assertTrue("脱离文档的拖拽应结束且不提交",
                defaultHtml.contains("finishPreviewTableResizeDrag(false)"));
        Assert.assertTrue("即时渲染、所见即所得和预览阅读区使用同一套表面",
                defaultHtml.contains(IR_TABLE_SURFACE)
                        && defaultHtml.contains(WYSIWYG_TABLE_SURFACE)
                        && defaultHtml.contains(PREVIEW_TABLE_SURFACE));
    }

    /**
     * 验证两张拖拽光标图都是 32×32 的 PNG。
     *
     * @throws IOException 光标图读取失败时抛出
     */
    @Test
    public void shouldKeepResizeCursorImagesAtStandardSize() throws IOException {
        int[] columnSize = readPngSize(COLUMN_RESIZE_CURSOR_PATH);
        int[] rowSize = readPngSize(ROW_RESIZE_CURSOR_PATH);

        Assert.assertEquals("列宽光标宽度应为 32", RESIZE_CURSOR_SIZE, columnSize[0]);
        Assert.assertEquals("列宽光标高度应为 32", RESIZE_CURSOR_SIZE, columnSize[1]);
        Assert.assertEquals("行高光标宽度应为 32", RESIZE_CURSOR_SIZE, rowSize[0]);
        Assert.assertEquals("行高光标高度应为 32", RESIZE_CURSOR_SIZE, rowSize[1]);
    }

    /**
     * 以 UTF-8 读取项目内文本文件。
     *
     * @param filePath 项目内相对路径
     * @return 文件全文
     * @throws IOException 文件不存在或读取失败时抛出
     */
    private static String readProjectFile(Path filePath) throws IOException {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 截取两个标记之间的模板片段。找不到时返回空串，让断言失败而不是抛出。
     *
     * @param text 完整模板
     * @param startMark 起始标记
     * @param endMark 结束标记
     * @return 含起始标记、不含结束标记的片段
     */
    private static String sliceBetween(String text, String startMark, String endMark) {
        int start = text.indexOf(startMark);
        if (start < 0) {
            return "";
        }
        int end = text.indexOf(endMark, start + startMark.length());
        if (end < 0) {
            return "";
        }
        return text.substring(start, end);
    }

    /**
     * 读取 PNG 的 IHDR 宽高。
     *
     * @param filePath 光标图路径
     * @return 下标 0 为宽度，下标 1 为高度
     * @throws IOException 文件读取失败时抛出
     */
    private static int[] readPngSize(Path filePath) throws IOException {
        byte[] data = Files.readAllBytes(filePath);
        Assert.assertTrue("光标图应为 PNG", data.length > 24 && data[0] == (byte) 0x89);
        int width = ((data[16] & 0xff) << 24)
                | ((data[17] & 0xff) << 16)
                | ((data[18] & 0xff) << 8)
                | (data[19] & 0xff);
        int height = ((data[20] & 0xff) << 24)
                | ((data[21] & 0xff) << 16)
                | ((data[22] & 0xff) << 8)
                | (data[23] & 0xff);
        return new int[]{width, height};
    }

    /**
     * 统计固定片段出现次数。
     *
     * @param text 完整文本
     * @param fragment 要统计的片段
     * @return 出现次数
     */
    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count += 1;
            index += fragment.length();
        }
        return count;
    }
}
