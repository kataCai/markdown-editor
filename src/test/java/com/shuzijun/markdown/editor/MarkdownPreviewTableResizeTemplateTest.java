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
        Assert.assertTrue("竖边热区为 6px，列宽下限为 36px",
                defaultHtml.contains("const PREVIEW_TABLE_RESIZE_HIT_PX = 6;")
                        && defaultHtml.contains("const PREVIEW_TABLE_COLUMN_MIN_PX = 36;"));
        Assert.assertTrue("贴边滚动为 24px 热区、每帧 16px",
                defaultHtml.contains("const PREVIEW_TABLE_EDGE_SCROLL_PX = 24;")
                        && defaultHtml.contains("const PREVIEW_TABLE_EDGE_SCROLL_STEP_PX = 16;"));
        Assert.assertTrue("列宽和行高光标应盖过全局默认光标",
                defaultHtml.contains("markdown-preview-table-col-resize")
                        && defaultHtml.contains("cursor: col-resize;")
                        && defaultHtml.contains("markdown-preview-table-row-resize")
                        && defaultHtml.contains("cursor: row-resize;"));
        Assert.assertTrue("指示线应挂在模式节点之外",
                defaultHtml.contains("markdown-preview-table-resize-guide")
                        && defaultHtml.contains("position: fixed;"));
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
