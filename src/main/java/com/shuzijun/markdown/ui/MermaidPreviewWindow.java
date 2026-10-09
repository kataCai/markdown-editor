package com.shuzijun.markdown.ui;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.shuzijun.markdown.editor.MarkdownHtmlPanel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mermaid 放大查看的整屏窗口。
 * 窗口使用当前 IDE 所在显示器的完整像素矩形，而不是预览标签页或 IDE Frame 的大小。
 */
public class MermaidPreviewWindow {

    private static final Logger LOG = Logger.getInstance(MermaidPreviewWindow.class);
    private static final String VIEWER_URL = "about:blank";
    private static final String VIEWER_TEMPLATE_PATH = "/template/mermaid-preview-viewer.html";
    private static final String SVG_MARKUP_PLACEHOLDER = "{{svgMarkupJson}}";
    private static final String BASE_WIDTH_PLACEHOLDER = "{{baseWidth}}";
    private static final String BASE_HEIGHT_PLACEHOLDER = "{{baseHeight}}";
    private static final String INJECT_SCRIPT_PLACEHOLDER = "{{injectScript}}";
    private static final String CLOSE_MESSAGE_TYPE = "previewMermaidClose";
    private static final String REPLACE_SVG_FUNCTION = "replaceMermaidPreviewSvg";
    private static final String QUEUED_SVG_FIELD = "__queuedMermaidPreview";
    private static final Map<Project, MermaidPreviewWindow> OPEN_WINDOWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final Project project;
    private final MarkdownHtmlPanel htmlPanel;
    private final JDialog dialog;
    private boolean disposed;

    /**
     * 打开或更新当前项目的 Mermaid 整屏查看窗口。
     * 同一项目只保留一个窗口，后续点击替换其中的 SVG。
     *
     * @param project    当前项目；为空时忽略本次请求
     * @param svgMarkup  已渲染 SVG 的 HTML 片段
     * @param baseWidth  SVG 基础宽度
     * @param baseHeight SVG 基础高度
     */
    public static void open(@Nullable Project project,
                            @Nullable String svgMarkup,
                            int baseWidth,
                            int baseHeight) {
        if (project == null || StringUtils.isBlank(svgMarkup)) {
            LOG.warn("Ignore mermaid preview request without project or svg");
            return;
        }
        String markup = svgMarkup;
        ApplicationManager.getApplication().invokeLater(
                () -> openOnEdt(project, markup, baseWidth, baseHeight)
        );
    }

    /**
     * 按 IDE 所在显示器计算整屏矩形。
     * 有 Frame 时选择与它相交面积最大的屏幕完整 bounds；没有 Frame，或和所有屏幕都不相交时，退回默认屏幕的完整矩形。
     *
     * @param ideFrameBounds       当前 IDE 主窗口边界，可以为空
     * @param screenBounds         各显示器的完整像素矩形
     * @param fallbackScreenBounds Frame 不可用时使用的默认屏幕完整矩形
     * @return 查看窗口应使用的屏幕矩形
     */
    @NotNull
    static Rectangle calculateFullScreenBounds(@Nullable Rectangle ideFrameBounds,
                                               @NotNull List<Rectangle> screenBounds,
                                               @NotNull Rectangle fallbackScreenBounds) {
        if (!hasPositiveSize(ideFrameBounds)) {
            return new Rectangle(fallbackScreenBounds);
        }
        Rectangle matchedScreen = findScreenWithLargestIntersection(ideFrameBounds, screenBounds);
        if (matchedScreen == null) {
            return new Rectangle(fallbackScreenBounds);
        }
        return new Rectangle(matchedScreen);
    }

    private MermaidPreviewWindow(@NotNull Project project,
                                 @NotNull String template,
                                 @NotNull String svgMarkup,
                                 int baseWidth,
                                 int baseHeight) {
        this.project = project;
        this.htmlPanel = new MarkdownHtmlPanel(VIEWER_URL, project, false);
        this.htmlPanel.setPreviewSyncMessageHandler(this::handleViewerMessage);
        this.dialog = createDialog();
        this.htmlPanel.loadMyHTML(buildViewerHtml(template, svgMarkup, baseWidth, baseHeight), VIEWER_URL);
        this.dialog.setContentPane(this.htmlPanel.getComponent());
    }

    private static void openOnEdt(@NotNull Project project,
                                  @NotNull String svgMarkup,
                                  int baseWidth,
                                  int baseHeight) {
        if (project.isDisposed()) {
            return;
        }
        MermaidPreviewWindow existingWindow = OPEN_WINDOWS.get(project);
        if (existingWindow != null && existingWindow.isShowing()) {
            existingWindow.replaceSvg(svgMarkup, baseWidth, baseHeight);
            return;
        }
        if (existingWindow != null) {
            existingWindow.disposeWindow();
        }
        String template = readViewerTemplate();
        if (template == null) {
            return;
        }
        MermaidPreviewWindow window = new MermaidPreviewWindow(project, template, svgMarkup, baseWidth, baseHeight);
        OPEN_WINDOWS.put(project, window);
        window.show();
    }

    @NotNull
    private static Rectangle resolveFullScreenBounds(@Nullable Rectangle ideFrameBounds) {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        return calculateFullScreenBounds(
                ideFrameBounds,
                collectScreenBounds(environment),
                defaultScreenBounds(environment)
        );
    }

    @NotNull
    private static List<Rectangle> collectScreenBounds(@NotNull GraphicsEnvironment environment) {
        List<Rectangle> screenBounds = new ArrayList<>();
        for (GraphicsDevice device : environment.getScreenDevices()) {
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            if (configuration != null) {
                screenBounds.add(configuration.getBounds());
            }
        }
        return screenBounds;
    }

    @NotNull
    private static Rectangle defaultScreenBounds(@NotNull GraphicsEnvironment environment) {
        GraphicsDevice device = environment.getDefaultScreenDevice();
        GraphicsConfiguration configuration = device == null ? null : device.getDefaultConfiguration();
        if (configuration == null) {
            return new Rectangle(0, 0, 1, 1);
        }
        return configuration.getBounds();
    }

    @Nullable
    private static Rectangle readIdeFrameBounds(@NotNull Project project) {
        Frame frame = WindowManager.getInstance().getFrame(project);
        if (frame == null) {
            return null;
        }
        Rectangle bounds = frame.getBounds();
        if (!hasPositiveSize(bounds)) {
            return null;
        }
        return bounds;
    }

    @Nullable
    private static Rectangle findScreenWithLargestIntersection(@NotNull Rectangle ideFrameBounds,
                                                               @NotNull List<Rectangle> screenBounds) {
        Rectangle matchedScreen = null;
        long largestArea = 0L;
        for (Rectangle screen : screenBounds) {
            long area = intersectionArea(ideFrameBounds, screen);
            if (area > largestArea) {
                largestArea = area;
                matchedScreen = screen;
            }
        }
        return matchedScreen;
    }

    private static long intersectionArea(@NotNull Rectangle first, @Nullable Rectangle second) {
        if (!hasPositiveSize(second)) {
            return 0L;
        }
        Rectangle intersection = first.intersection(second);
        if (!hasPositiveSize(intersection)) {
            return 0L;
        }
        return (long) intersection.width * intersection.height;
    }

    private static boolean hasPositiveSize(@Nullable Rectangle bounds) {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    @Nullable
    private static String readViewerTemplate() {
        try (InputStream inputStream = MermaidPreviewWindow.class.getResourceAsStream(VIEWER_TEMPLATE_PATH)) {
            if (inputStream == null) {
                LOG.error("Mermaid preview viewer template is missing");
                return null;
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOG.error("Failed to read mermaid preview viewer template", exception);
            return null;
        }
    }

    @NotNull
    private String buildViewerHtml(@NotNull String template,
                                   @NotNull String svgMarkup,
                                   int baseWidth,
                                   int baseHeight) {
        return template
                .replace(SVG_MARKUP_PLACEHOLDER, toJavaScriptStringLiteral(svgMarkup))
                .replace(BASE_WIDTH_PLACEHOLDER, Integer.toString(baseWidth))
                .replace(BASE_HEIGHT_PLACEHOLDER, Integer.toString(baseHeight))
                .replace(INJECT_SCRIPT_PLACEHOLDER, htmlPanel.getInjectScript());
    }

    @NotNull
    private static String toJavaScriptStringLiteral(@NotNull String value) {
        return JSONObject.toJSONString(value).replace("<", "\\u003c");
    }

    @NotNull
    private JDialog createDialog() {
        Frame owner = WindowManager.getInstance().getFrame(project);
        JDialog viewerDialog = new JDialog(owner);
        viewerDialog.setUndecorated(true);
        viewerDialog.setAlwaysOnTop(true);
        viewerDialog.setModal(false);
        viewerDialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        viewerDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                disposeWindow();
            }
        });
        bindEscape(viewerDialog);
        return viewerDialog;
    }

    private void bindEscape(@NotNull JDialog viewerDialog) {
        viewerDialog.getRootPane().registerKeyboardAction(
                event -> disposeWindow(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void show() {
        Rectangle bounds = resolveFullScreenBounds(readIdeFrameBounds(project));
        dialog.setBounds(bounds);
        dialog.setVisible(true);
        dialog.setBounds(bounds);
        dialog.toFront();
    }

    private boolean isShowing() {
        return !disposed && dialog.isShowing();
    }

    private void replaceSvg(@NotNull String svgMarkup, int baseWidth, int baseHeight) {
        htmlPanel.getCefBrowser().executeJavaScript(
                buildReplaceScript(svgMarkup, baseWidth, baseHeight),
                VIEWER_URL,
                0
        );
        dialog.toFront();
    }

    @NotNull
    private static String buildReplaceScript(@NotNull String svgMarkup, int baseWidth, int baseHeight) {
        String literal = toJavaScriptStringLiteral(svgMarkup);
        return "window." + QUEUED_SVG_FIELD
                + "={markup:" + literal
                + ",width:" + baseWidth
                + ",height:" + baseHeight
                + "};if(typeof " + REPLACE_SVG_FUNCTION
                + "==='function'){" + REPLACE_SVG_FUNCTION
                + "(" + literal + "," + baseWidth + "," + baseHeight + ");}";
    }

    private void handleViewerMessage(@Nullable JSONObject message) {
        if (message == null) {
            return;
        }
        if (CLOSE_MESSAGE_TYPE.equals(message.getString("type"))) {
            disposeWindow();
        }
    }

    private void disposeWindow() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (OPEN_WINDOWS.get(project) == this) {
            OPEN_WINDOWS.remove(project);
        }
        htmlPanel.dispose();
        dialog.dispose();
    }
}
