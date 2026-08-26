package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

/**
 * Markdown 预览模板选择测试。
 * 这些测试用于约束预览页在 bundled template 与 external template 之间的选择策略，
 * 重点覆盖“外部模板缺少宿主注入占位符时必须回退”的回归风险。
 * 如果这里放任旧模板继续生效，就会直接退回到页内图片预览链路，导致本次独立窗口改造失效。
 */
public class MarkdownPreviewFileEditorTemplateSelectionTest {

    /**
     * 验证外部模板缺少 `{{injectScript}}` 占位符时，会强制回退到内置模板。
     * 这个断言直接覆盖本次问题的关键根因之一：
     * 一旦运行时命中了旧 external template，而它又没有给宿主脚本留注入口，
     * 图片点击 hook 就无法被宿主强制接管，最终只能退回旧的页内预览行为。
     */
    @Test
    public void shouldFallbackToBundledTemplateWhenExternalTemplateMissesInjectScriptPlaceholder() {
        String bundledTemplate = "<html><body>{{injectScript}}</body></html>";
        String externalTemplate = "<html><body>legacy external template</body></html>";

        Assert.assertEquals(
                bundledTemplate,
                MarkdownPreviewFileEditor.resolvePreviewTemplate(bundledTemplate, externalTemplate)
        );
    }

    /**
     * 验证外部模板仍然保留宿主脚本注入口时，继续优先使用外部模板。
     * 这里覆盖的是兼容性边界：修复不能粗暴禁用外部模板，而是只在它破坏宿主注入能力时才降级。
     */
    @Test
    public void shouldKeepExternalTemplateWhenInjectScriptPlaceholderStillExists() {
        String bundledTemplate = "<html><body>{{injectScript}}</body></html>";
        String externalTemplate = "<html><body>custom {{injectScript}} template</body></html>";

        Assert.assertEquals(
                externalTemplate,
                MarkdownPreviewFileEditor.resolvePreviewTemplate(bundledTemplate, externalTemplate)
        );
    }
}
