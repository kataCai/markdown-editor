package com.shuzijun.markdown.model;

import com.intellij.openapi.application.PathManager;

import java.io.File;

/**
 * @author shuzijun
 */
public class PluginConstant {

    public static final String PLUGIN_ID = "com.shuzijun.markdown-editor";

    public static final String NOTIFICATION_GROUP = "Markdown editor";
    public static final String APPLICATION_CONFIGURABLE_DISPLAY_NAME = "Markdown editor";

    public static final String EDITOR_TOOLBAR = "Markdown Editor Toolbar";

    public static final String TEMPLATE_VERSION = "1";
    public static final String TEMPLATE_PATH = PathManager.getPluginsPath() + File.separator + "markdown-editor" + File.separator + "assets" + File.separator;

    public static final  String JS_DELIVR_ENDPOINTS = "https://data.jsdelivr.com/v1/package/gh/shuzijun/markdown-editor";
    public static final String CDN = "https://cdn.jsdelivr.net/gh/shuzijun/markdown-editor@";

    public static final String editorPolicyKey="markdown.editor.editorPolicy";

    public static final String editorAssetsPathKey="markdown.editor.editorAssetsPath";

    public static final String editorAssetsNameAutoKey="markdown.editor.editorAssetsName";

    public static final String editorFixToolbarKey="markdown.editor.editorFixToolbar";

    public static final String editorTextOperationKey="markdown.editor.editorTextOperation";

    public static final String editorAbsolutePathKey="markdown.editor.editorAbsolutePath";

    /**
     * Markdown 预览页顶部网页工具栏的全局默认显隐状态。
     * 该配置控制的是 Vditor 页面内部工具栏，不是 IntelliJ IDE 原生工具栏。
     */
    public static final String editorPreviewToolbarVisibleKey = "markdown.editor.previewToolbarVisible";

    /**
     * Markdown 预览页是否允许直接编辑的全局默认状态。
     * 默认值由读取方使用 false 控制，表示新打开的预览页默认不可编辑。
     */
    public static final String editorPreviewEditableKey = "markdown.editor.previewEditable";
}
