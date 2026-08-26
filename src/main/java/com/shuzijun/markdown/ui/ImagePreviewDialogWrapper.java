package com.shuzijun.markdown.ui;

import com.alibaba.fastjson.JSONObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.components.JBPanel;
import com.shuzijun.markdown.editor.MarkdownHtmlPanel;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Markdown 预览图片独立查看对话框。
 * 该对话框负责承接预览页点击图片后的查看请求，并在 IDE 级窗口中展示图片，
 * 从而摆脱当前 Markdown 编辑区内部 JCEF 视口的尺寸限制。
 * 窗口内部仍然复用 JCEF 加载图片 URL，这样既能兼容本地文件、内置静态服务和远程地址，
 * 也能继续复用浏览器层面的缩放、滚动和图片解码能力。
 */
public class ImagePreviewDialogWrapper extends DialogWrapper {

    private static final String VIEWER_URL = "about:blank";
    private static final int MIN_VIEWER_WIDTH = 640;
    private static final int MIN_VIEWER_HEIGHT = 480;
    private static final double MIN_SCALE = 0.1d;
    private static final double MAX_SCALE = 4.0d;
    private static final double SCALE_STEP = 0.2d;

    private final String imageUrl;
    private final String imageAlt;
    private final String imageTitle;
    private final Rectangle initialBounds;
    private final JPanel rootPanel;
    private final MarkdownHtmlPanel htmlPanel;

    /**
     * 初始化图片查看对话框。
     * 构造阶段会先按当前 IDE Frame 计算完整窗口边界，再创建 JCEF 查看页并显式设置对话框的位置与尺寸。
     * 这样首次打开时就直接与整个 Android Studio 主窗口对齐，而不是继续停留在 Markdown 编辑区局部范围内。
     *
     * @param project 当前项目上下文；允许为空，取不到项目窗口时会退回到当前屏幕可用区
     * @param imageUrl 需要展示的图片地址
     * @param imageAlt 图片替代文本
     * @param imageTitle 图片标题
     */
    public ImagePreviewDialogWrapper(@Nullable Project project,
                                     @NotNull String imageUrl,
                                     @Nullable String imageAlt,
                                     @Nullable String imageTitle) {
        super(project, true);
        this.imageUrl = imageUrl;
        this.imageAlt = imageAlt;
        this.imageTitle = imageTitle;
        this.initialBounds = calculateDialogBounds(getIdeFrameBounds(project), getFallbackScreenBounds());
        this.rootPanel = new JBPanel<>(new BorderLayout());
        this.rootPanel.setPreferredSize(initialBounds.getSize());
        this.rootPanel.setMinimumSize(new Dimension(
                Math.min(MIN_VIEWER_WIDTH, initialBounds.width),
                Math.min(MIN_VIEWER_HEIGHT, initialBounds.height)
        ));
        this.htmlPanel = new MarkdownHtmlPanel(VIEWER_URL, project, false);
        this.htmlPanel.loadMyHTML(buildHtml(), VIEWER_URL);
        this.rootPanel.add(htmlPanel.getComponent(), BorderLayout.CENTER);
        setModal(false);
        setResizable(true);
        init();
        setSize(initialBounds.width, initialBounds.height);
        setLocation(initialBounds.x, initialBounds.y);
        setTitle(buildTitle());
    }

    /**
     * 返回查看器首次展示时的窗口尺寸。
     * 这里显式绑定到 `initialBounds`，避免 `DialogWrapper` 再次按内容首选尺寸回退到局部编辑区尺度。
     *
     * @return 独立查看窗口的初始尺寸
     */
    @Override
    public @NotNull Dimension getInitialSize() {
        return initialBounds.getSize();
    }

    /**
     * 返回查看器首次展示时的窗口位置。
     * 这里直接对齐 IDE Frame 左上角，保证独立图片窗口与整个 IDE 主窗口保持同一屏幕位置基准。
     *
     * @return 独立查看窗口的初始位置
     */
    @Override
    public @NotNull Point getInitialLocation() {
        return initialBounds.getLocation();
    }

    /**
     * 构建对话框中心内容。
     * 这里直接返回承载 JCEF 图片查看页的根面板，避免额外包装层影响窗口尺寸计算。
     *
     * @return 对话框中心区域组件
     */
    @Override
    protected @Nullable JComponent createCenterPanel() {
        return rootPanel;
    }

    /**
     * 只保留一个关闭动作，避免图片查看器出现与业务无关的确认按钮。
     * 该动作仅负责关闭独立查看窗口，不会触发任何编辑或导出副作用。
     *
     * @return 关闭动作
     */
    @Override
    protected @NotNull Action getCancelAction() {
        Action action = super.getCancelAction();
        action.putValue(Action.NAME, "Close");
        return action;
    }

    /**
     * 仅暴露关闭动作为底部按钮。
     * 图片查看器没有“提交”语义，保留单一关闭入口可以减少误操作。
     *
     * @return 对话框动作数组
     */
    @Override
    protected @NotNull Action[] createActions() {
        return new Action[]{getCancelAction()};
    }

    /**
     * 释放图片查看器使用的 JCEF 资源。
     * 对话框关闭后必须主动释放浏览器实例，避免重复打开时残留旧页面或额外占用内存。
     */
    @Override
    public void dispose() {
        htmlPanel.dispose();
        super.dispose();
    }

    /**
     * 计算图片查看器应当采用的窗口边界。
     * 优先直接复用当前 IDE Frame 的完整边界，只有在无法获取有效 Frame 时才退回到屏幕可用区。
     * 这一步是本次改造的关键：只有窗口边界本身与 IDE 对齐，查看器才不会继续被编辑区尺寸限制。
     *
     * @param ideFrameBounds 当前 IDE 主窗口边界；为空或无效时表示暂时无法直接对齐 IDE Frame
     * @param fallbackBounds 无法获取 IDE Frame 时的兜底边界，通常来自当前屏幕的可用区
     * @return 最终用于独立窗口初始展示的边界矩形
     */
    @NotNull
    static Rectangle calculateDialogBounds(@Nullable Rectangle ideFrameBounds, @NotNull Rectangle fallbackBounds) {
        if (ideFrameBounds != null && ideFrameBounds.width > 0 && ideFrameBounds.height > 0) {
            return new Rectangle(ideFrameBounds);
        }
        return new Rectangle(fallbackBounds);
    }

    /**
     * 获取当前项目所在 IDE 主窗口的完整边界。
     * 这里刻意返回 `Rectangle` 而不是仅返回宽高，
     * 以便后续同时控制独立窗口的尺寸和屏幕位置。
     *
     * @param project 当前项目上下文
     * @return IDE 主窗口边界；取不到时返回 {@code null}
     */
    @Nullable
    private static Rectangle getIdeFrameBounds(@Nullable Project project) {
        Frame frame = project == null ? null : WindowManager.getInstance().getFrame(project);
        if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
            return null;
        }
        return frame.getBounds();
    }

    /**
     * 获取当前屏幕可用区边界作为兜底窗口边界。
     * 当项目尚未绑定到有效 IDE Frame，或者窗口初始化时机过早时，
     * 这里提供稳定的可视区域兜底，避免查看器因为空边界又退回到内容首选尺寸。
     *
     * @return 当前屏幕可用区边界
     */
    @NotNull
    private static Rectangle getFallbackScreenBounds() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
    }

    /**
     * 生成图片查看器窗口标题。
     * 若图片自带 title 或 alt，则优先在标题中体现，便于用户在多窗口并排时快速确认当前查看对象。
     *
     * @return 对话框标题
     */
    @NotNull
    private String buildTitle() {
        String candidate = StringUtils.defaultIfBlank(imageTitle, imageAlt);
        return StringUtils.isBlank(candidate) ? "Image Preview" : "Image Preview - " + candidate;
    }

    /**
     * 构建图片查看器的独立 HTML 页面。
     * 页面内部提供基础缩放、重置和自适应逻辑，图片则继续通过浏览器直接加载原始 URL。
     * 这样可以兼容本地文件、内置服务地址和远程图片，同时继续复用浏览器的解码与缩放能力。
     *
     * @return 可直接加载到 JCEF 的 HTML 文本
     */
    @NotNull
    private String buildHtml() {
        String imageUrlJs = JSONObject.toJSONString(imageUrl);
        String imageAltJs = JSONObject.toJSONString(StringUtils.defaultString(imageAlt));
        String imageTitleJs = JSONObject.toJSONString(StringUtils.defaultString(imageTitle));

        StringBuilder html = new StringBuilder(4096);
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\" />");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />");
        html.append("<style>");
        html.append("html,body{width:100%;height:100%;margin:0;overflow:hidden;background:#1f2329;color:#d7dde5;font-family:-apple-system,BlinkMacSystemFont,\"Segoe UI\",sans-serif;}");
        html.append(".viewer{display:flex;flex-direction:column;width:100%;height:100%;}");
        html.append(".toolbar{flex:0 0 auto;display:flex;align-items:center;gap:8px;padding:12px 16px;border-bottom:1px solid rgba(255,255,255,.08);background:#171a20;box-sizing:border-box;}");
        html.append(".toolbar button{height:30px;min-width:30px;padding:0 10px;border:1px solid rgba(255,255,255,.12);border-radius:6px;background:#252a33;color:#f3f6fb;cursor:pointer;}");
        html.append(".toolbar button:hover{background:#314d79;border-color:rgba(129,169,255,.64);}");
        html.append(".toolbar .title{flex:1 1 auto;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#9aa4b2;font-size:12px;}");
        html.append(".toolbar .scale{min-width:56px;text-align:right;font-variant-numeric:tabular-nums;color:#f3f6fb;}");
        html.append(".viewport{flex:1 1 auto;overflow:auto;padding:24px;box-sizing:border-box;}");
        html.append(".canvas{position:relative;margin:0 auto;display:flex;align-items:flex-start;justify-content:flex-start;}");
        html.append(".loading{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;color:#9aa4b2;font-size:14px;}");
        html.append(".image{display:none;max-width:none;max-height:none;transform-origin:top left;}");
        html.append("</style></head><body>");
        html.append("<div class=\"viewer\">");
        html.append("<div class=\"toolbar\">");
        html.append("<button type=\"button\" onclick=\"zoomOut()\">-</button>");
        html.append("<button type=\"button\" onclick=\"zoomIn()\">+</button>");
        html.append("<button type=\"button\" onclick=\"fitToViewport()\">Fit</button>");
        html.append("<button type=\"button\" onclick=\"resetZoom()\">100%</button>");
        html.append("<div id=\"title\" class=\"title\"></div>");
        html.append("<div id=\"scale\" class=\"scale\">100%</div>");
        html.append("</div>");
        html.append("<div id=\"viewport\" class=\"viewport\">");
        html.append("<div id=\"canvas\" class=\"canvas\">");
        html.append("<div id=\"loading\" class=\"loading\">Loading...</div>");
        html.append("<img id=\"image\" class=\"image\" alt=").append(imageAltJs).append(" title=").append(imageTitleJs).append(" />");
        html.append("</div></div></div>");
        html.append("<script>");
        html.append("const IMAGE_URL=").append(imageUrlJs).append(";");
        html.append("const IMAGE_ALT=").append(imageAltJs).append(";");
        html.append("const IMAGE_TITLE=").append(imageTitleJs).append(";");
        html.append("const MIN_SCALE=").append(MIN_SCALE).append(";");
        html.append("const MAX_SCALE=").append(MAX_SCALE).append(";");
        html.append("const SCALE_STEP=").append(SCALE_STEP).append(";");
        html.append("let scale=1;");
        html.append("let fitMode=true;");
        html.append("let naturalWidth=0;");
        html.append("let naturalHeight=0;");
        html.append("const image=document.getElementById('image');");
        html.append("const loading=document.getElementById('loading');");
        html.append("const canvas=document.getElementById('canvas');");
        html.append("const viewport=document.getElementById('viewport');");
        html.append("const scaleLabel=document.getElementById('scale');");
        html.append("const titleLabel=document.getElementById('title');");
        html.append("titleLabel.textContent=IMAGE_TITLE || IMAGE_ALT || IMAGE_URL;");
        html.append("image.alt=IMAGE_ALT;");
        html.append("image.title=IMAGE_TITLE || IMAGE_ALT;");
        html.append("function clampScale(nextScale){return Math.max(MIN_SCALE,Math.min(MAX_SCALE,nextScale));}");
        html.append("function updateCanvasSize(){");
        html.append("  const width=Math.max(1,Math.round(naturalWidth*scale));");
        html.append("  const height=Math.max(1,Math.round(naturalHeight*scale));");
        html.append("  canvas.style.width=width+'px';");
        html.append("  canvas.style.height=height+'px';");
        html.append("  image.style.width=width+'px';");
        html.append("  image.style.height=height+'px';");
        html.append("  scaleLabel.textContent=Math.round(scale*100)+'%';");
        html.append("}");
        html.append("function applyScale(nextScale, keepFitMode){");
        html.append("  if(!naturalWidth || !naturalHeight){return;}");
        html.append("  scale=clampScale(nextScale);");
        html.append("  if(keepFitMode===false){fitMode=false;}");
        html.append("  updateCanvasSize();");
        html.append("}");
        html.append("function fitToViewport(){");
        html.append("  if(!naturalWidth || !naturalHeight){return;}");
        html.append("  const availableWidth=Math.max(1,viewport.clientWidth-48);");
        html.append("  const availableHeight=Math.max(1,viewport.clientHeight-48);");
        html.append("  fitMode=true;");
        html.append("  applyScale(Math.min(1,availableWidth/naturalWidth,availableHeight/naturalHeight), true);");
        html.append("}");
        html.append("function zoomIn(){applyScale(scale+SCALE_STEP, false);}");
        html.append("function zoomOut(){applyScale(scale-SCALE_STEP, false);}");
        html.append("function resetZoom(){fitMode=true;applyScale(1, true);}");
        html.append("image.onload=function(){");
        html.append("  naturalWidth=image.naturalWidth || 0;");
        html.append("  naturalHeight=image.naturalHeight || 0;");
        html.append("  loading.style.display='none';");
        html.append("  image.style.display='block';");
        html.append("  fitToViewport();");
        html.append("};");
        html.append("image.onerror=function(){");
        html.append("  loading.textContent='Failed to load image';");
        html.append("};");
        html.append("window.updateHeight=function(){};");
        html.append("window.addEventListener('resize', function(){ if(fitMode){ fitToViewport(); } });");
        html.append("image.src=IMAGE_URL;");
        html.append("</script></body></html>");
        return html.toString();
    }
}
