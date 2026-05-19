package com.shuzijun.markdown.editor;

import com.alibaba.fastjson.JSONObject;
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.event.EditorEventMulticaster;
import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
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
import com.intellij.util.Alarm;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.URLUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.shuzijun.markdown.controller.FileApplicationService;
import com.shuzijun.markdown.controller.PreviewStaticServer;
import com.shuzijun.markdown.editor.sync.EditorActivationSyncSupport;
import com.shuzijun.markdown.editor.sync.EditorActivationTransitionSupport;
import com.shuzijun.markdown.editor.sync.EditorViewportSyncSupport;
import com.shuzijun.markdown.editor.sync.PreviewEditorSyncCoordinator;
import com.shuzijun.markdown.editor.sync.PreviewSyncMessage;
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
    private static final int PREVIEW_TO_EDITOR_SUPPRESS_MS = 280;
    private static final int EDITOR_TO_PREVIEW_SUPPRESS_MS = 280;
    /**
     * 预览同步协调器。
     * 该对象集中维护内容版本、预览页 ready/rendered 状态以及 reveal 挂起/补发逻辑，
     * 避免这些判断继续散落在 UI 组件和事件监听器中。
     */
    private final PreviewEditorSyncCoordinator syncCoordinator;
    /**
     * 文档内容推送节流器。
     * Markdown 源码变更时不直接每次按键都触发整页 `setValue`，而是做轻量延迟合并，降低 JCEF 重绘压力。
     */
    private final Alarm contentSyncAlarm;

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
        syncCoordinator = new PreviewEditorSyncCoordinator(myFile.getPath());
        contentSyncAlarm = new Alarm(this);
        syncCoordinator.updateDocument(myDocument.getText(), myDocument.getModificationStamp());
        initToolbarPanel();
        rebuildPreviewPanel();
        initSyncInfrastructure();

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
        syncCoordinator.resetPreviewLifecycle();
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
        panel.setPreviewSyncMessageHandler(this::handlePreviewSyncMessage);
        panel.loadMyHTML(createHtml(isPresentableUrl, panel), previewUrl);
        panel.setPreviewEditable(isPreviewEditable());
        return panel;
    }

    /**
     * 初始化宿主侧同步基础设施。
     * 这里集中绑定文档变更、源码编辑器滚动/光标变化以及文件编辑器激活切换等监听入口，
     * 为后续“源码 <-> 预览”联动提供统一事件源。
     */
    private void initSyncInfrastructure() {
        EditorEventMulticaster eventMulticaster = com.intellij.openapi.editor.EditorFactory.getInstance().getEventMulticaster();
        eventMulticaster.addDocumentListener(new DocumentListener() {
            @Override
            public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
                if (event.getDocument() != myDocument) {
                    return;
                }
                scheduleContentSync("documentChanged");
            }
        }, this);
        eventMulticaster.addCaretListener(new CaretListener() {
            @Override
            public void caretPositionChanged(@NotNull CaretEvent event) {
                Editor editor = event.getEditor();
                if (!isPrimarySourceEditor(editor)) {
                    return;
                }
                if (syncCoordinator.getState().isEditorEventSuppressed(System.currentTimeMillis())) {
                    return;
                }
                revealPreviewForEditor(editor, "caret");
            }
        }, this);
        eventMulticaster.addVisibleAreaListener(new VisibleAreaListener() {
            @Override
            public void visibleAreaChanged(@NotNull VisibleAreaEvent event) {
                Editor editor = event.getEditor();
                if (!isPrimarySourceEditor(editor)) {
                    return;
                }
                if (syncCoordinator.getState().isEditorEventSuppressed(System.currentTimeMillis())) {
                    return;
                }
                revealPreviewForEditor(editor, "scroll");
            }
        }, this);

        MessageBusConnection editorSelectionConnection = myProject.getMessageBus().connect(this);
        editorSelectionConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent event) {
                if (event.getNewFile() != null && !myFile.equals(event.getNewFile())) {
                    return;
                }
                if (event.getNewEditor() == MarkdownPreviewFileEditor.this) {
                    scheduleContentSync("activation");
                    Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
                    if (editor != null && editor.getDocument() == myDocument) {
                        revealPreviewForEditor(editor, "activation");
                    }
                    return;
                }
                if (event.getNewEditor() instanceof com.intellij.openapi.fileEditor.TextEditor) {
                    boolean shouldRestore = EditorActivationTransitionSupport.shouldRestoreSourceFromPreview(
                            event.getOldEditor() == MarkdownPreviewFileEditor.this,
                            true
                    );
                    Editor sourceEditor = ((com.intellij.openapi.fileEditor.TextEditor) event.getNewEditor()).getEditor();
                    if (sourceEditor.getDocument() != myDocument) {
                        return;
                    }
                    if (!shouldRestore) {
                        debugSync("skip source activation restore: oldEditorIsPreview=%s, newEditor=%s",
                                event.getOldEditor() == MarkdownPreviewFileEditor.this,
                                event.getNewEditor().getClass().getSimpleName());
                        return;
                    }
                    int targetLine = syncCoordinator.getState().resolveSourceActivationTargetLine();
                    debugSync("source activation restore target=%s, previewSelectionStart=%s, previewAnchorLine=%s, previewAnchorKind=%s",
                            targetLine,
                            syncCoordinator.getState().getPreviewSelectionStartLine(),
                            syncCoordinator.getState().getPreviewAnchorLine(),
                            syncCoordinator.getState().getPreviewAnchorKind());
                    if (targetLine >= 0) {
                        revealSourceEditorLine(sourceEditor, targetLine);
                    }
                }
            }
        });
    }

    /**
     * 处理来自预览页的统一同步消息。
     * 当前阶段优先接入 ready / rendered / dirty 三条最关键链路，用于打通内容版本同步和宿主覆盖保护。
     *
     * @param message 预览页发回的结构化消息
     */
    private void handlePreviewSyncMessage(@NotNull JSONObject message) {
        String type = message.getString("type");
        if (StringUtils.isBlank(type)) {
            return;
        }
        switch (type) {
            case PreviewSyncMessage.TYPE_PREVIEW_READY:
                dispatchPreviewMessages(syncCoordinator.handlePreviewReady());
                break;
            case PreviewSyncMessage.TYPE_PREVIEW_RENDERED:
                dispatchPreviewMessages(syncCoordinator.handlePreviewRendered(message.getLongValue("contentVersion")));
                break;
            case PreviewSyncMessage.TYPE_PREVIEW_DIRTY_CHANGED:
                syncCoordinator.handlePreviewDirtyChanged(message.getJSONObject("payload").getBooleanValue("dirty"));
                break;
            case PreviewSyncMessage.TYPE_PREVIEW_VIEWPORT_CHANGED:
                handlePreviewViewportChanged(message.getJSONObject("payload"));
                break;
            case PreviewSyncMessage.TYPE_PREVIEW_SELECTION_CHANGED:
                handlePreviewSelectionChanged(message.getJSONObject("payload"));
                break;
            case PreviewSyncMessage.TYPE_PREVIEW_SELECTION_CLEARED:
                handlePreviewSelectionCleared();
                break;
            default:
                break;
        }
    }

    /**
     * 安排一次节流后的 Markdown 内容同步。
     * 该同步会刷新协调器中的当前 Markdown 版本，并在页面 ready 且非脏状态时向预览页推送 applyMarkdown。
     *
     * @param reason 触发原因，用于调试和后续扩展时识别入口
     */
    private void scheduleContentSync(@NotNull String reason) {
        contentSyncAlarm.cancelAllRequests();
        contentSyncAlarm.addRequest(() -> {
            dispatchPreviewMessages(syncCoordinator.updateDocument(myDocument.getText(), myDocument.getModificationStamp()));
        }, "activation".equals(reason) ? 0 : 120);
    }

    /**
     * 将给定源码编辑器的当前位置同步到预览页。
     * 当前实现先用逻辑行号 + 可视区相对比例形成最小联动闭环，后续再叠加更精细的顶部/底部对齐策略。
     *
     * @param editor 当前参与联动的源码编辑器
     * @param reason 触发原因，例如 caret、scroll、activation
     */
    private void revealPreviewForEditor(@NotNull Editor editor, @NotNull String reason) {
        EditorViewportSyncSupport.ViewportAnchor viewportAnchor = resolveEditorViewportAnchor(editor, reason);
        int line = viewportAnchor.getLine();
        double topRatio = calculateEditorTopRatio(editor, line);
        long now = System.currentTimeMillis();
        if (!"activation".equals(reason) && syncCoordinator.getState().shouldSkipDuplicatedEditorReveal(line, topRatio, now)) {
            return;
        }
        syncCoordinator.getState().updateEditorAnchor(line, topRatio);
        syncCoordinator.getState().recordEditorRevealDispatch(line, topRatio, now);
        syncCoordinator.getState().suppressPreviewEventsUntil(now + EDITOR_TO_PREVIEW_SUPPRESS_MS);
        debugSync("reveal preview for editor: reason=%s, line=%s, topRatio=%.3f", reason, line, topRatio);
        dispatchPreviewMessages(syncCoordinator.requestRevealSourceLine(line, topRatio, reason));
    }

    /**
     * 解析当前源码编辑器在本次事件下应同步到预览页的语义锚点。
     * 光标事件使用 caret 行；滚动事件按顶部/中线/底部策略取锚点，减少 `sv` 模式和长文档中的跳动感。
     *
     * @param editor 当前参与联动的源码编辑器
     * @param reason 触发原因，例如 caret、scroll、activation
     * @return 当前事件应使用的视口语义锚点
     */
    @NotNull
    private EditorViewportSyncSupport.ViewportAnchor resolveEditorViewportAnchor(@NotNull Editor editor, @NotNull String reason) {
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        int topLine = editor.xyToLogicalPosition(new Point(visibleArea.x, visibleArea.y)).line;
        int bottomLine = editor.xyToLogicalPosition(new Point(visibleArea.x, Math.max(visibleArea.y, visibleArea.y + Math.max(visibleArea.height - 1, 0)))).line;
        int middleLine = editor.xyToLogicalPosition(new Point(visibleArea.x, visibleArea.y + Math.max(visibleArea.height / 2, 0))).line;
        int caretLine = editor.getCaretModel().getLogicalPosition().line;
        return EditorViewportSyncSupport.resolveAnchor(
                reason,
                caretLine,
                topLine,
                bottomLine,
                middleLine,
                editor.getDocument().getLineCount()
        );
    }

    /**
     * 计算指定源码逻辑行在当前可视区中的相对高度比例。
     * 该比例用于让预览页尽量以与源码编辑器一致的视口语义位置显示目标内容。
     *
     * @param editor 当前源码编辑器
     * @param line   需要换算的逻辑行号
     * @return 0 到 1 之间的相对比例；若当前可视区高度无效，则返回 0
     */
    private double calculateEditorTopRatio(@NotNull Editor editor, int line) {
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        if (visibleArea.height <= 0) {
            return 0d;
        }
        Point linePoint = editor.logicalPositionToXY(new com.intellij.openapi.editor.LogicalPosition(Math.max(line, 0), 0));
        double ratio = (linePoint.y - visibleArea.y) / (double) visibleArea.height;
        if (ratio < 0d) {
            return 0d;
        }
        if (ratio > 1d) {
            return 1d;
        }
        return ratio;
    }

    /**
     * 判断给定编辑器是否是当前文件在当前项目中的主源码编辑器事件来源。
     * 这里先按“同项目 + 同文档 + 当前选中文本编辑器”收敛，避免其他文件或后台编辑器污染联动状态。
     *
     * @param editor 待判断的编辑器
     * @return {@code true} 表示该编辑器事件可参与当前预览联动
     */
    private boolean isPrimarySourceEditor(@Nullable Editor editor) {
        if (editor == null || editor.isDisposed()) {
            return false;
        }
        if (editor.getProject() != myProject || editor.getDocument() != myDocument) {
            return false;
        }
        return editor == FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    }

    /**
     * 将协调器产出的结构化消息逐条发送给当前预览页。
     *
     * @param messages 待发送的消息列表
     */
    private void dispatchPreviewMessages(@NotNull java.util.List<PreviewSyncMessage> messages) {
        if (myPanel == null || messages.isEmpty()) {
            return;
        }
        for (PreviewSyncMessage message : messages) {
            myPanel.sendPreviewSyncMessage(message);
        }
    }

    /**
     * 将源码编辑器定位到指定逻辑行。
     * 当前实现先恢复到该行附近，后续再根据预览选区范围补充更精细的区间可见性对齐。
     *
     * @param line 目标逻辑行号
     */
    private void revealSourceEditorLine(@NotNull Editor editor, int line) {
        long nowMillis = System.currentTimeMillis();
        if (shouldSkipSourceRestore(editor, line, nowMillis)) {
            debugSync("skip source restore as no-op: targetLine=%s, visibleTopBottom=%s-%s",
                    line,
                    editor.xyToLogicalPosition(new Point(editor.getScrollingModel().getVisibleArea().x, editor.getScrollingModel().getVisibleArea().y)).line,
                    editor.xyToLogicalPosition(new Point(
                            editor.getScrollingModel().getVisibleArea().x,
                            editor.getScrollingModel().getVisibleArea().y + Math.max(editor.getScrollingModel().getVisibleArea().height - 1, 0)
                    )).line);
            return;
        }
        syncCoordinator.getState().recordSourceRestore(line, nowMillis);
        syncCoordinator.getState().suppressEditorEventsUntil(nowMillis + PREVIEW_TO_EDITOR_SUPPRESS_MS);
        debugSync("reveal source editor: targetLine=%s", line);
        FileEditorManager.getInstance(myProject).openTextEditor(
                new OpenFileDescriptor(myProject, myFile, Math.max(line, 0), 0).setUseCurrentWindow(true),
                true
        );
    }

    /**
     * 判断本次“切回源码编辑器”的恢复定位是否属于无效恢复。
     * 当前会拦截两类情况：
     * 1. 目标行本来就在可视区内，此时再次恢复只会制造抖动；
     * 2. 极短时间内对同一目标行重复恢复，此时大概率属于激活回声或布局抖动。
     *
     * @param editor     当前源码编辑器
     * @param targetLine 本次准备恢复的目标逻辑行号
     * @param nowMillis  当前时间戳
     * @return {@code true} 表示应跳过本次恢复，避免无意义跳动
     */
    private boolean shouldSkipSourceRestore(@NotNull Editor editor, int targetLine, long nowMillis) {
        Rectangle visibleArea = editor.getScrollingModel().getVisibleArea();
        int topLine = editor.xyToLogicalPosition(new Point(visibleArea.x, visibleArea.y)).line;
        int bottomLine = editor.xyToLogicalPosition(new Point(
                visibleArea.x,
                visibleArea.y + Math.max(visibleArea.height - 1, 0)
        )).line;
        return EditorActivationSyncSupport.shouldSkipSourceRestore(
                topLine,
                bottomLine,
                targetLine,
                syncCoordinator.getState().getLastRestoredSourceLine(),
                syncCoordinator.getState().getLastRestoredSourceAt(),
                nowMillis
        );
    }

    /**
     * 处理预览页滚动后回写的源码锚点。
     * 当前阶段优先恢复到对应逻辑行附近，并更新宿主状态中的最近预览锚点。
     *
     * @param payload 预览页回传的结构化 payload
     */
    private void handlePreviewViewportChanged(@Nullable JSONObject payload) {
        if (payload == null || syncCoordinator.getState().isPreviewEventSuppressed(System.currentTimeMillis())) {
            return;
        }
        int anchorLine = payload.getIntValue("anchorLine");
        debugSync("receive preview viewport: anchorLine=%s, sourceId=%s, anchorKind=%s",
                anchorLine,
                payload.getString("sourceId"),
                payload.getString("anchorKind"));
        syncCoordinator.getState().updatePreviewAnchor(
                anchorLine,
                payload.getString("sourceId"),
                resolvePreviewAnchorKind(payload.getString("anchorKind"))
        );
        Editor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        if (selectedEditor != null && selectedEditor.getDocument() == myDocument) {
            revealSourceEditorLine(selectedEditor, anchorLine);
        }
    }

    /**
     * 处理预览页选区变化回写。
     * 当前实现先记录选区的源码行范围，并在源码编辑器处于当前活动态时恢复到选区起始行附近。
     *
     * @param payload 预览页回传的结构化 payload
     */
    private void handlePreviewSelectionChanged(@Nullable JSONObject payload) {
        if (payload == null || syncCoordinator.getState().isPreviewEventSuppressed(System.currentTimeMillis())) {
            return;
        }
        debugSync("receive preview selection: start=%s, end=%s, text=%s",
                payload.getIntValue("startLine"),
                payload.getIntValue("endLine"),
                payload.getString("selectedTextPreview"));
        syncCoordinator.getState().updatePreviewAnchor(
                payload.getIntValue("startLine"),
                payload.getString("sourceId"),
                com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.USER_SELECTION
        );
        syncCoordinator.getState().updatePreviewSelection(
                payload.getIntValue("startLine"),
                payload.getIntValue("endLine"),
                payload.getString("selectedTextPreview")
        );
        Editor selectedEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        if (selectedEditor != null && selectedEditor.getDocument() == myDocument) {
            revealSourceEditorLine(selectedEditor, payload.getIntValue("startLine"));
        }
    }

    /**
     * 处理预览页显式上报的“选区已清空”事件。
     * 该事件用于及时清除宿主侧陈旧选区状态，避免后续激活切换始终优先消费历史选区。
     */
    private void handlePreviewSelectionCleared() {
        syncCoordinator.getState().clearPreviewSelection();
        debugSync("receive preview selection cleared");
    }

    /**
     * 将预览页回传的锚点来源字符串映射为宿主侧枚举。
     * 未知来源默认按用户滚动处理，避免因前后端升级不同步而把正常回写全部拦掉。
     *
     * @param anchorKind 预览页回传的锚点来源
     * @return 宿主侧锚点类型
     */
    @NotNull
    private com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind resolvePreviewAnchorKind(@Nullable String anchorKind) {
        if ("NO_SCROLL_SYNTHETIC".equals(anchorKind)) {
            return com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.NO_SCROLL_SYNTHETIC;
        }
        if ("PROGRAMMATIC_REVEAL".equals(anchorKind)) {
            return com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.PROGRAMMATIC_REVEAL;
        }
        if ("ACTIVATION_SYNC".equals(anchorKind)) {
            return com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.ACTIVATION_SYNC;
        }
        if ("USER_SELECTION".equals(anchorKind)) {
            return com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.USER_SELECTION;
        }
        return com.shuzijun.markdown.editor.sync.PreviewEditorSyncState.PreviewAnchorKind.USER_SCROLL;
    }

    /**
     * 按需输出编辑器/预览联动诊断日志。
     * 该日志默认关闭，仅在显式打开调试开关时用于排查人工验证中的事件顺序、旧选区滞留和误恢复问题。
     *
     * @param format 日志格式串
     * @param args   日志参数
     */
    private void debugSync(@NotNull String format, Object... args) {
        if (!PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewSyncDebugKey, false)) {
            return;
        }
        LOG.info("[preview-sync] " + String.format(format, args));
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

    /**
     * 切换当前预览页是否允许编辑。
     * 该方法只同步当前已打开的预览实例，全局默认值由右键菜单 Action 持久化。
     *
     * @param editable {@code true} 表示允许编辑，{@code false} 表示禁止编辑
     */
    public void setPreviewEditable(boolean editable) {
        if (myPanel != null) {
            myPanel.setPreviewEditable(editable);
        }
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
                    .replace("{{previewEditable}}", String.valueOf(isPreviewEditable()))
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
     * 读取预览页可编辑状态的全局默认值。
     *
     * @return {@code true} 表示默认允许编辑，{@code false} 表示默认禁止编辑
     */
    private boolean isPreviewEditable() {
        return PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewEditableKey, false);
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
