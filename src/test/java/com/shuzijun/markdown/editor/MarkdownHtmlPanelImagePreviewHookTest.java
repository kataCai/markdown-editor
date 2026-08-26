package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

/**
 * Markdown 预览宿主注入脚本测试。
 * 这些测试不验证模板源码文本，而是验证 Java 侧最终生成的注入脚本契约，
 * 确保宿主始终会在运行时重新安装图片点击 hook，而不是把行为完全绑定到某一份模板文件。
 */
public class MarkdownHtmlPanelImagePreviewHookTest {

    /**
     * 验证宿主注入脚本会强制安装图片预览 hook，并把点击事件回传为 `previewImageRequest`。
     * 只要有人删掉安装入口、消息类型或 `vditor.options.image.preview` 覆盖逻辑，
     * 这个测试就应该立即失败，避免再次退回到页内图片预览。
     */
    @Test
    public void shouldInstallImagePreviewHookThroughInjectedScript() {
        String script = MarkdownHtmlPanel.buildPreviewImageHookScript();

        Assert.assertTrue("宿主注入脚本必须声明独立的安装入口，便于运行时重复接管旧模板", script.contains("installMarkdownEditorImagePreviewHook"));
        Assert.assertTrue("宿主注入脚本必须覆盖 vditor.options.image.preview，才能接管图片点击", script.contains("vditor.options.image.preview"));
        Assert.assertTrue("宿主注入脚本必须通过统一桥接通道回传消息，避免再次散落单点 JS 调用", script.contains("window.previewSyncBridge"));
        Assert.assertTrue("宿主注入脚本必须回传 previewImageRequest，Java 侧才会打开 IDE 级独立窗口", script.contains("previewImageRequest"));
    }
}
