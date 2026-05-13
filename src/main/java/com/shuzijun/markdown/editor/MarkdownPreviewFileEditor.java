package com.shuzijun.markdown.editor;

import com.google.common.escape.Escaper;
import com.google.common.net.PercentEscaper;
import com.google.common.net.UrlEscapers;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.ScrollBarPainter;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.shuzijun.markdown.controller.FileApplicationService;
import com.shuzijun.markdown.controller.PreviewStaticServer;
import com.shuzijun.markdown.model.PluginConstant;
import com.shuzijun.markdown.util.FileUtils;
import com.shuzijun.markdown.util.PropertiesUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Markdown 预览编辑器。
 * 该编辑器负责在 IntelliJ 的文件编辑器体系中承载基于 JCEF 的 Markdown 预览页面，
 * 同时管理页面初始化、顶部搜索工具栏、主题样式同步以及与预览页面交互相关的宿主容器状态。
 * 在本次改造中，这个类还承担了“强制刷新当前页面”的恢复职责：
 * 当预览页出现偶发白屏时，通过销毁旧的 JCEF 面板并重建新实例来尽量模拟“关闭后重新打开标签页”的恢复路径。
 */
public class MarkdownPreviewFileEditor extends UserDataHolderBase implements FileEditor {

    private static final Logger LOG = Logger.getInstance(MarkdownPreviewFileEditor.class);

    static final String URL_PATH_OTHER_SAFE_CHARS_LACKING_PLUS =
            "-._~" // Unreserved characters.
                    + "!$'()*,;&=" // The subdelim characters (excluding '+').
                    + "@:" // The gendelim characters permitted in paths.
                    + "/?"; // PATH
    private static final Escaper URL_FRAGMENT_ESCAPER = new PercentEscaper(URL_PATH_OTHER_SAFE_CHARS_LACKING_PLUS, true);

    private final Project myProject;
    private final VirtualFile myFile;
    private final Document myDocument;

    private final JPanel myHtmlPanelWrapper;
    /**
     * 当前生效的预览面板实例。
     * 这里不再声明为 final，是因为“强制刷新当前页面”需要销毁旧的 JCEF 面板并创建新实例，
     * 以尽量模拟“关闭标签页后重新打开”的恢复路径，提升白屏场景下的恢复成功率。
     */
    private MarkdownHtmlPanel myPanel;

    private final JBPanel toolbarPanel = new JBPanel(new FlowLayout(FlowLayout.LEFT));
    private final JBTextField searchField = new JBTextField();

    private final Url servicePath = BuiltInServerManager.getInstance().addAuthToken(Urls.parseEncoded("http://localhost:" + BuiltInServerManager.getInstance().getPort() + PreviewStaticServer.PREFIX));
    private final String templateHtmlFile = "template/default.html";
    private final boolean isPresentableUrl;
    private final String previewUrl;

    /**
     * 初始化 Markdown 预览编辑器。
     * 构造过程会准备文件上下文、创建顶部工具栏并首次构建预览面板，
     * 同时注册 IDE 主题变化监听，确保后续可以将样式更新同步到当前预览页。
     *
     * @param project 当前项目上下文
     * @param file    当前打开的 Markdown 文件
     */
    public MarkdownPreviewFileEditor(@NotNull Project project, @NotNull VirtualFile file) {
        myProject = project;
        myFile = file;
        myDocument = FileDocumentManager.getInstance().getDocument(myFile);
        myHtmlPanelWrapper = new JPanel(new BorderLayout());
        isPresentableUrl = project.getPresentableUrl() != null;
        previewUrl = UrlEscapers.urlFragmentEscaper().escape(URLUtil.FILE_PROTOCOL + URLUtil.SCHEME_SEPARATOR + FileUtils.separator() + myFile.getPath());
        initToolbarPanel();
        rebuildPreviewPanel();

        FileApplicationService fileApplicationService = ApplicationManager.getApplication().getService(FileApplicationService.class);
        fileApplicationService.putVirtualFile(myFile.getPath(), isPresentableUrl ? project.getPresentableUrl() : project.getName(), myFile);

        MessageBusConnection settingsConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
        settingsConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
            @Override
            public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
                if (myPanel != null) {
                    myPanel.updateStyle(getStyle(false));
                }
            }
        });

    }

    /**
     * 初始化顶部搜索工具栏和相关交互。
     * 这里的事件监听不直接捕获首次创建的预览面板实例，而是每次都读取当前 {@link #myPanel}，
     * 这样在强制刷新后，搜索和翻页操作仍然会作用到新的 JCEF 面板上，避免引用已经释放的旧对象。
     */
    private void initToolbarPanel() {
        searchField.setPreferredSize(new Dimension(200, 25));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    browserFind(searchField.getText(), true);
                }
            }
        });
        searchField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                browserFind(searchField.getText(), true);
            }
        });

        JBLabel previousLabel = new JBLabel("<", JLabel.CENTER);
        previousLabel.setPreferredSize(new Dimension(25, 25));
        previousLabel.addMouseListener(new LabelMouseListener(previousLabel, false));

        JBLabel nextLabel = new JBLabel(">", JLabel.CENTER);
        nextLabel.setPreferredSize(new Dimension(25, 25));
        nextLabel.addMouseListener(new LabelMouseListener(nextLabel, true));

        JBLabel close = new JBLabel("x", JLabel.CENTER);
        close.setPreferredSize(new Dimension(25, 25));
        close.addMouseListener(new LabelMouseListener(close, false) {
            @Override
            public void mouseClicked(MouseEvent e) {
                visibleToolbarPanel(false);
            }
        });

        toolbarPanel.add(new JBLabel("find:", JLabel.CENTER));
        toolbarPanel.add(searchField);
        toolbarPanel.add(previousLabel);
        toolbarPanel.add(nextLabel);
        toolbarPanel.add(close);

        AnAction searchAction = ActionManager.getInstance().getAction("markdown.search");
        AnAction searchVisibleAction = ActionManager.getInstance().getAction("markdown.searchVisible");
        ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(PluginConstant.EDITOR_TOOLBAR, new DefaultActionGroup(searchAction, searchVisibleAction), true);
        actionToolbar.setTargetComponent(myHtmlPanelWrapper);
        JComponent actionToolbarComponent = actionToolbar.getComponent();
        actionToolbarComponent.setVisible(false);
        toolbarPanel.add(actionToolbarComponent);
        toolbarPanel.setVisible(false);
        myHtmlPanelWrapper.add(toolbarPanel, BorderLayout.NORTH);
    }

    /**
     * 重建当前 Markdown 预览面板。
     * 该方法会销毁旧的 JCEF 面板并重新创建新实例，使“强制刷新”尽量贴近“关闭标签页后重新打开”的恢复语义，
     * 从而在白屏场景下比简单浏览器 reload 具备更高的恢复概率。
     */
    private void rebuildPreviewPanel() {
        MarkdownHtmlPanel oldPanel = myPanel;
        if (oldPanel != null) {
            myHtmlPanelWrapper.remove(oldPanel.getComponent());
        } else {
            Component oldCenter = ((BorderLayout) myHtmlPanelWrapper.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            if (oldCenter != null) {
                myHtmlPanelWrapper.remove(oldCenter);
            }
        }

        MarkdownHtmlPanel newPanel = null;
        JComponent centerComponent;
        try {
            newPanel = createPreviewPanel();
            centerComponent = newPanel.getComponent();
        } catch (IllegalStateException e) {
            LOG.warn("Failed to rebuild markdown preview panel", e);
            centerComponent = new JBLabel(e.getMessage());
        }

        myPanel = newPanel;
        myHtmlPanelWrapper.add(centerComponent, BorderLayout.CENTER);
        myHtmlPanelWrapper.revalidate();
        myHtmlPanelWrapper.repaint();

        if (oldPanel != null) {
            Disposer.dispose(oldPanel);
        }
    }

    /**
     * 创建并初始化新的预览面板实例。
     * 这里会重新生成 HTML 模板并加载到新的 JCEF 面板中，确保页面脚本、工具栏和资源请求处理器一并重新初始化。
     *
     * @return 已完成 HTML 装载的预览面板实例
     */
    private @NotNull MarkdownHtmlPanel createPreviewPanel() {
        MarkdownHtmlPanel panel = new MarkdownHtmlPanel(previewUrl, myProject, true);
        panel.loadMyHTML(createHtml(isPresentableUrl, panel), previewUrl);
        return panel;
    }

    /**
     * 将页面内查找请求转发到当前激活的预览面板。
     * 当预览面板被强制刷新并替换后，搜索栏仍然通过该方法统一访问当前实例，避免误操作已销毁的旧浏览器对象。
     *
     * @param text    需要查找的文本
     * @param forward {@code true} 表示向后查找，{@code false} 表示向前查找
     */
    private void browserFind(String text, boolean forward) {
        if (myPanel != null) {
            myPanel.browserFind(text, forward);
        }
    }


    @Override
    public @NotNull JComponent getComponent() {
        return myHtmlPanelWrapper;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return myPanel != null ? myPanel.getComponent() : null;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getName() {
        return "Markdown Editor";
    }

    @Override
    public void setState(@NotNull FileEditorState state) {

    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

    }

    @Override
    public @Nullable FileEditorLocation getCurrentLocation() {
        return null;
    }

    @Override
    public StructureViewBuilder getStructureViewBuilder() {
        VirtualFile file = FileDocumentManager.getInstance().getFile(myDocument);
        if (file == null || !file.isValid()) return null;
        return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.getFileType(), file, myProject);
    }

    @Override
    public void dispose() {
        ApplicationManager.getApplication().getService(FileApplicationService.class)
                .removeVirtualFile(myFile.getPath(), isPresentableUrl ? myProject.getPresentableUrl() : myProject.getName());
        if (myPanel != null) {
            Disposer.dispose(myPanel);
        }
    }

    /**
     * 打开当前预览页的开发者工具。
     * 该方法仅在当前预览面板仍然有效时转发调用，避免对已经释放的面板实例执行调试动作。
     */
    public void openDevtools() {
        if (myPanel != null) {
            myPanel.openDevtools();
        }
    }

    /**
     * 强制刷新当前预览页面。
     * 这里不采用简单的浏览器 reload，而是主动销毁并重建当前预览面板，
     * 用已被用户验证有效的“重新创建预览实例”方式来恢复偶发白屏场景。
     */
    public void forceRefreshPreview() {
        if (myPanel != null) {
            myPanel.browserFind("", true);
        }
        visibleToolbarPanel(false);
        rebuildPreviewPanel();
    }

    private String createHtml(boolean isPresentableUrl, MarkdownHtmlPanel tempPanel) {
        InputStream inputStream = null;

        try {
            File templateFile = new File(PluginConstant.TEMPLATE_PATH + templateHtmlFile);
            if (templateFile.exists()) {
                inputStream = new FileInputStream(templateFile);
            } else {
                inputStream = PreviewStaticServer.class.getResourceAsStream("/" + templateHtmlFile);
            }
            String template = new String(FileUtilRt.loadBytes(inputStream));
            return template.replace("{{service}}", servicePath.getScheme() + URLUtil.SCHEME_SEPARATOR + servicePath.getAuthority() + servicePath.getPath())
                    .replace("{{serverToken}}", StringUtils.isNotBlank(servicePath.getParameters()) ? servicePath.getParameters().substring(1) : "")
                    .replace("{{filePath}}", URL_FRAGMENT_ESCAPER.escape(myFile.getPath()))
                    .replace("{{Lang}}", PropertiesUtils.getInfo("Lang"))
                    .replace("{{darcula}}", UIUtil.isUnderDarcula() + "")
                    .replace("{{userTemplate}}", templateFile.exists() + "")
                    .replace("{{projectUrl}}", isPresentableUrl ? URL_FRAGMENT_ESCAPER.escape(myProject.getPresentableUrl()) : "")
                    .replace("{{projectName}}", isPresentableUrl ? "" : URL_FRAGMENT_ESCAPER.escape(myProject.getName()))
                    .replace("{{previewToolbarVisible}}", String.valueOf(isPreviewToolbarVisible()))
                    .replace("{{ideStyle}}", getStyle(true))
                    .replace("{{injectScript}}", tempPanel.getInjectScript())
                    ;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    @Override
    public @Nullable VirtualFile getFile() {
        return myFile;
    }

    private String getStyle(boolean isTag) {
        try {
            EditorColorsSchemeImpl editorColorsScheme = (EditorColorsSchemeImpl) EditorColorsManager.getInstance().getGlobalScheme();
            Color defaultBackground = editorColorsScheme.getDefaultBackground();

            Color scrollbarThumbColor = ScrollBarPainter.THUMB_OPAQUE_BACKGROUND.getDefaultColor();
            if (editorColorsScheme.getColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND) != null) {
                scrollbarThumbColor = editorColorsScheme.getColor(ScrollBarPainter.THUMB_OPAQUE_BACKGROUND);
            }

            Color text = editorColorsScheme.getDefaultForeground();
            String fontFamily = "font-family:\"" + editorColorsScheme.getEditorFontName() + "\",\"Helvetica Neue\",\"Luxi Sans\",\"DejaVu Sans\"," +
                    "\"Hiragino Sans GB\",\"Microsoft Yahei\",sans-serif,\"Apple Color Emoji\",\"Segoe UI Emoji\",\"Noto Color Emoji\",\"Segoe UI Symbol\"," +
                    "\"Android Emoji\",\"EmojiSymbols\";";
            StringBuilder sb = new StringBuilder(isTag ? "<style id=\"ideaStyle\">" : "");
            sb.append(UIUtil.isUnderDarcula() ? ".vditor--dark" : ".vditor").append("{--panel-background-color:").append(toHexColor(defaultBackground))
                    .append(";--textarea-background-color:").append(toHexColor(defaultBackground)).append(";");
            sb.append("--toolbar-background-color:").append(toHexColor(JBColor.background())).append(";");
            sb.append("}");
            sb.append("::-webkit-scrollbar-track {background-color:").append(toHexColor(defaultBackground)).append(";}");
            sb.append("::-webkit-scrollbar-thumb {background-color:").append(toHexColor(scrollbarThumbColor)).append(";}");
            sb.append(".vditor-reset {font-size:").append(editorColorsScheme.getEditorFontSize()).append("px;");
            sb.append(fontFamily);
            if (text != null) {
                sb.append("color:").append(toHexColor(text)).append(";");
            }
            sb.append("}");
            if (text != null) {
                sb.append(".vditor-reset table {color:").append(toHexColor(text)).append(";}");
            }
            sb.append(isTag ? "</style>" : "");
            LOG.info("markdown style: " + sb + " ; Darcula: " + UIUtil.isUnderDarcula());
            return sb.toString();
        } catch (Exception e) {
            LOG.info("Failed to create style", e);
            return "";
        }

    }

    private String toHexColor(Color color) {
        DecimalFormat df = new DecimalFormat("0.00");
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();
        dfs.setDecimalSeparator('.');
        df.setDecimalFormatSymbols(dfs);
        return String.format("rgba(%s,%s,%s,%s)", color.getRed(), color.getGreen(), color.getBlue(), df.format(color.getAlpha() / (float) 255));
    }

    /**
     * 控制顶部搜索工具栏的显示状态。
     * 当工具栏关闭时，会同步清空当前页面查找状态，避免刷新或隐藏后仍然残留浏览器内的高亮结果。
     *
     * @param visible {@code true} 表示显示搜索工具栏，{@code false} 表示隐藏
     */
    public void visibleToolbarPanel(boolean visible) {
        toolbarPanel.setVisible(visible);
        browserFind("", true);
        if (visible) {
            searchField.requestFocus();
        } else {
            searchField.setText("");
        }
    }

    /**
     * 切换预览页顶部工具栏显示状态。
     * 这里既要更新当前编辑器实例中的网页工具栏，也要在隐藏时顺带收起搜索条，
     * 避免出现网页工具栏已关闭但搜索浮条仍残留在顶部的状态不一致问题。
     *
     * @param visible {@code true} 表示显示预览工具栏，{@code false} 表示隐藏
     */
    public void setPreviewToolbarVisible(boolean visible) {
        if (myPanel != null) {
            myPanel.setPreviewToolbarVisible(visible);
        }
        if (!visible) {
            visibleToolbarPanel(false);
        }
    }

    /**
     * 读取预览工具栏的全局默认显示状态。
     *
     * @return {@code true} 表示默认显示，{@code false} 表示默认隐藏
     */
    private boolean isPreviewToolbarVisible() {
        return PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewToolbarVisibleKey, false);
    }

    /**
     * 搜索工具栏按钮的鼠标事件监听器。
     * 该监听器服务于“上一条”“下一条”和关闭按钮，
     * 并在点击时统一转发到当前有效的预览面板，避免按钮事件仍然指向旧的 JCEF 实例。
     */
    private class LabelMouseListener extends MouseAdapter {

        private JBLabel label;

        private boolean forward;

        private Color color;

        /**
         * 初始化搜索工具栏按钮监听器。
         *
         * @param label   当前绑定的标签组件
         * @param forward {@code true} 表示向后搜索，{@code false} 表示向前搜索
         */
        public LabelMouseListener(JBLabel label, boolean forward) {
            this.label = label;
            this.forward = forward;
        }

        /**
         * 触发搜索翻页动作。
         * 这里统一调用外层编辑器的查找转发方法，使按钮在强制刷新后仍然作用于新的预览实例。
         *
         * @param e 鼠标点击事件
         */
        @Override
        public void mouseClicked(MouseEvent e) {
            browserFind(searchField.getText(), forward);
        }

        /**
         * 在鼠标按下时临时高亮按钮，提供轻量交互反馈。
         *
         * @param e 鼠标按下事件
         */
        @Override
        public void mousePressed(MouseEvent e) {
            color = label.getForeground();
            label.setForeground(Color.BLUE);
        }

        /**
         * 在鼠标释放时恢复按钮前景色，避免按钮长时间停留在按下态颜色。
         *
         * @param e 鼠标释放事件
         */
        @Override
        public void mouseReleased(MouseEvent e) {
            label.setForeground(color);
        }
    }
}
