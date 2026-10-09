package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mermaid 升级资源验证测试。
 * 该测试类用于给这次 Mermaid 升级补一层低成本但稳定的回归护栏，重点覆盖三类风险：
 * 1. 版本声明与实际打包资源再次脱节，导致排查时误判真实运行版本。
 * 2. {@code default.html} 与升级后的 Vditor 接线被后续改动破坏，出现“预览可用但导出失效”或反向失效。
 * 3. 缺少一组固定的 Mermaid 验收样例，后续继续升级时没有可重复使用的最小回归基线。
 * 这些测试不尝试替代 JCEF 真机预览验证，而是把当前分支已经确认的重要静态事实固化下来，
 * 让后续改动一旦破坏版本来源、导出链路或基线样例范围时，能够在 `gradlew test` 阶段尽早暴露。
 */
public class MermaidUpgradeResourceVerificationTest {

    /**
     * `package.json` 中声明的 Vditor 版本。
     * 该常量用于约束版本记录与打包资源头标识保持一致，避免再次出现“声明版本”和“实际运行版本”分离。
     */
    private static final String EXPECTED_VDITOR_VERSION = "3.11.2";

    /**
     * 当前内置 Mermaid 运行时版本。
     * 该常量对应静态资源里已经打包进仓库、并通过本次升级接入的 Mermaid 版本来源。
     */
    private static final String EXPECTED_MERMAID_VERSION = "11.6.0";

    /**
     * 仓库内 `package.json` 的相对路径。
     */
    private static final Path PACKAGE_JSON_PATH = Path.of("package.json");

    /**
     * Vditor 样式资源路径。
     */
    private static final Path VDITOR_INDEX_CSS_PATH = Path.of("src/main/resources/vditor/dist/index.css");

    /**
     * 导出场景使用的 Vditor 方法资源路径。
     */
    private static final Path VDITOR_METHOD_MIN_JS_PATH = Path.of("src/main/resources/vditor/dist/method.min.js");

    /**
     * 预览页实际加载的 Vditor 打包脚本。
     */
    private static final Path VDITOR_INDEX_MIN_JS_PATH = Path.of("src/main/resources/vditor/dist/index.min.js");

    /**
     * 预览和导出都必须带上的 Mermaid 标签测量配置。
     */
    private static final String MERMAID_HTML_LABEL_CONFIG =
            "htmlLabels:!0,flowchart:{wrappingWidth:480,useMaxWidth:!0}";

    /**
     * 只作用于 Mermaid 代码块的换行替换。源码里的反斜杠 n 要换成 br。
     */
    private static final String MERMAID_BACKSLASH_N_REPLACE = "textContent.replace(/\\\\n/g,\"<br/>\")";

    /**
     * Mermaid 实际运行时资源路径。
     */
    private static final Path MERMAID_MIN_JS_PATH = Path.of("src/main/resources/vditor/dist/js/mermaid/mermaid.min.js");

    /**
     * 预览页模板路径。
     */
    private static final Path DEFAULT_HTML_PATH = Path.of("src/main/resources/template/default.html");

    /**
     * 整屏 Mermaid 查看页模板。
     * 放大、拖拽和关闭已经从预览页挪到这份独立页面。
     */
    private static final Path MERMAID_PREVIEW_VIEWER_PATH = Path.of("src/main/resources/template/mermaid-preview-viewer.html");

    /**
     * Vditor 类型声明路径。
     * 这里用它来静态校验 `default.html` 中使用的配置键仍在 3.11.2 暴露的公开接口内。
     */
    private static final Path VDITOR_TYPES_PATH = Path.of("src/main/resources/vditor/dist/types/index.d.ts");

    /**
     * Mermaid 升级基线样例资源路径。
     * 这份样例用于人工回归预览、HTML 导出和 PDF 导出三条链路。
     */
    private static final Path MERMAID_BASELINE_PATH = Path.of("src/test/resources/mermaid/upgrade-baseline.md");

    /**
     * 验证当前仓库中记录的 Vditor 与 Mermaid 版本来源保持一致。
     * 该测试同时检查 `package.json`、Vditor 样式头标识、导出运行时以及 Mermaid 实际包内版本，
     * 目的是防止后续只更新部分文件，导致“声明版本”和“真实运行版本”再次错位。
     *
     * @throws IOException 当项目资源读取失败时抛出，用于直接暴露版本来源不可访问的问题
     */
    @Test
    public void shouldKeepBundledVditorAndMermaidVersionsAligned() throws IOException {
        String packageJson = readProjectFile(PACKAGE_JSON_PATH);
        String indexCss = readProjectFile(VDITOR_INDEX_CSS_PATH);
        String methodMinJs = readProjectFile(VDITOR_METHOD_MIN_JS_PATH);
        String mermaidMinJs = readProjectFile(MERMAID_MIN_JS_PATH);

        Assert.assertTrue("package.json 中应记录当前 Vditor 版本",
                packageJson.contains("\"vditor\": \"" + EXPECTED_VDITOR_VERSION + "\""));
        Assert.assertTrue("index.css 头标识应反映当前 Vditor 版本",
                indexCss.contains("Vditor v" + EXPECTED_VDITOR_VERSION));
        Assert.assertTrue("method.min.js 应继续加载当前 Mermaid 版本资源",
                methodMinJs.contains("mermaid.min.js?v=" + EXPECTED_MERMAID_VERSION));
        Assert.assertTrue("method.min.js 应继续走 Mermaid 11 的 render 调用链",
                methodMinJs.contains("mermaid.render("));
        Assert.assertTrue("实际打包的 mermaid.min.js 应包含当前 Mermaid 版本元数据",
                mermaidMinJs.contains("version:\""+ EXPECTED_MERMAID_VERSION + "\"")
                        || mermaidMinJs.contains("version:\"" + EXPECTED_MERMAID_VERSION + "\"")
                        || mermaidMinJs.contains("version:'" + EXPECTED_MERMAID_VERSION + "'")
                        || mermaidMinJs.contains("version:\""+ EXPECTED_MERMAID_VERSION + "\""));
    }

    /**
     * 验证 `default.html` 仍然通过 Vditor 3.11.2 的公开配置和导出 API 接入 Mermaid。
     * 该测试不模拟浏览器真实渲染，而是静态约束两件事：
     * 1. 模板中使用的关键配置项仍在 Vditor 类型定义里公开可用。
     * 2. HTML 导出链路仍然显式调用 `Vditor.mermaidRender(...)`，避免后续误删 Mermaid 导出接线。
     *
     * @throws IOException 当模板或类型定义读取失败时抛出，用于暴露验证前置资源缺失
     */
    @Test
    public void shouldKeepDefaultHtmlCompatibleWithBundledVditorContract() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String vditorTypes = readProjectFile(VDITOR_TYPES_PATH);

        Assert.assertTrue("default.html 应继续通过 new Vditor(...) 初始化编辑器",
                defaultHtml.contains("const vditor = new Vditor('vditor', {"));
        Assert.assertTrue("default.html 应继续配置本地 Vditor CDN 路径",
                defaultHtml.contains("\"cdn\": vditorCDN"));
        Assert.assertTrue("default.html 的导出链路应继续调用 Mermaid 渲染入口",
                defaultHtml.contains("Vditor.mermaidRender(previewElement, '\" + options.cdn + \"', '\" + options.mode + \"');"));
        Assert.assertTrue("default.html 的导出链路应继续走 Vditor.md2html",
                defaultHtml.contains("Vditor.md2html(vditor.getValue(), options)"));

        Assert.assertTrue("Vditor 类型定义应继续暴露 height 配置",
                vditorTypes.contains("height?: number | string;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 lang 配置",
                vditorTypes.contains("lang?: (keyof II18n);"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 cache 配置",
                vditorTypes.contains("cache?: {"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 toolbar 配置",
                vditorTypes.contains("toolbar?: Array<string | IMenuItem>;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 toolbarConfig 配置",
                vditorTypes.contains("toolbarConfig?: IToolbarConfig;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 preview 配置",
                vditorTypes.contains("preview?: IPreview;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 counter 配置",
                vditorTypes.contains("counter?: {"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 upload 配置",
                vditorTypes.contains("upload?: IUpload;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 theme 配置",
                vditorTypes.contains("theme?: \"classic\" | \"dark\";"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 after/focus/blur/input 回调",
                vditorTypes.contains("after?(): void;")
                        && vditorTypes.contains("focus?(value: string): void;")
                        && vditorTypes.contains("blur?(value: string): void;")
                        && vditorTypes.contains("input?(value: string): void;"));
        Assert.assertTrue("Vditor 类型定义应继续暴露 Mermaid 静态渲染方法签名",
                vditorTypes.contains("static mermaidRender: (element: HTMLElement | Document, cdn: string, theme: string) => void;")
                        || readProjectFile(Path.of("src/main/resources/vditor/dist/method.d.ts"))
                        .contains("static mermaidRender: (element: HTMLElement | Document, cdn: string, theme: string) => void;"));
    }

    /**
     * 验证预览页仍负责 Mermaid 按钮和重扫，并把放大请求发给宿主。
     * 查看层本身不再挂在预览页里，避免放大结果继续被当前标签页视口裁住。
     *
     * @throws IOException 当模板资源读取失败时抛出，用于直接暴露预览页模板缺失的问题
     */
    @Test
    public void shouldKeepDefaultHtmlWiredForMermaidPreviewViewerHooks() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String viewerHtml = readProjectFile(MERMAID_PREVIEW_VIEWER_PATH);

        Assert.assertTrue("default.html 应继续保留 Mermaid 渲染块装饰入口",
                defaultHtml.contains("function decorateMermaidPreviewBlocks()"));
        Assert.assertTrue("default.html 应继续保留 Mermaid 放大请求入口",
                defaultHtml.contains("function openMermaidPreviewViewer("));
        Assert.assertTrue("default.html 应继续保留 Mermaid 渲染结果异步重扫所需的 MutationObserver",
                defaultHtml.contains("new MutationObserver("));
        Assert.assertTrue("default.html 应把放大请求发给宿主",
                defaultHtml.contains("previewMermaidRequest"));
        Assert.assertTrue("default.html 应序列化当前 SVG，而不是在预览页挂载查看层",
                defaultHtml.contains("function serializeMermaidPreviewSvg(")
                        && !defaultHtml.contains("markdown-preview-figure-viewer"));
        Assert.assertTrue("独立查看页应保留关闭、缩放和事件判断入口",
                viewerHtml.contains("function closeMermaidPreviewViewer()")
                        && viewerHtml.contains("function updateMermaidPreviewScale(")
                        && viewerHtml.contains("function isMermaidPreviewViewerEventTarget("));
    }

    /**
     * 验证独立查看页仍然保留拖拽、抓手光标和紧凑工具条。
     * 这些结构已经离开预览页，后续样式整理如果只改 default.html，这条测试仍能发现查看页被改丢。
     *
     * @throws IOException 当查看页模板读取失败时抛出
     */
    @Test
    public void shouldKeepDefaultHtmlWiredForMermaidPreviewViewerDragAndToolbarLayout() throws IOException {
        String viewerHtml = readProjectFile(MERMAID_PREVIEW_VIEWER_PATH);

        Assert.assertTrue("查看页应继续保留 Mermaid 查看层拖拽绑定入口",
                viewerHtml.contains("function bindMermaidPreviewDrag("));
        Assert.assertTrue("查看页应继续保留 Mermaid 查看层拖拽状态清理入口",
                viewerHtml.contains("function finishMermaidPreviewDrag("));
        Assert.assertTrue("查看页应继续保留 Mermaid 查看层默认抓手光标",
                viewerHtml.contains("cursor: grab;"));
        Assert.assertTrue("查看页应继续保留 Mermaid 查看层拖拽中抓取光标",
                viewerHtml.contains("cursor: grabbing;"));
        Assert.assertTrue("查看页应继续保留 Mermaid 查看层工具条分组容器 class",
                viewerHtml.contains("markdown-preview-figure-viewer__toolbar-group"));
        Assert.assertTrue("查看页应继续在查看层打开时绑定拖拽能力",
                viewerHtml.contains("bindMermaidPreviewDrag(viewportElement);"));
        Assert.assertTrue("查看页应用 HTML 片段插入 SVG，并保留标签换行规则",
                viewerHtml.contains("content.innerHTML = markup || \"\";")
                        && viewerHtml.contains("foreignObject div")
                        && viewerHtml.contains("white-space: pre-line !important;"));
    }

    /**
     * 验证 Mermaid 查看层工具栏使用稳定的内联 SVG 图标，而不是依赖字体或文字字符表现图标。
     * 该测试覆盖参考交互中的四个固定操作：放大、缩小、重置和关闭；同时约束按钮尺寸为紧凑方形，
     * 防止后续样式调整再次退化成宽大的文字按钮，或因运行环境字体差异导致图标形状不一致。
     *
     * @throws IOException 当模板资源读取失败时抛出，用于直接暴露预览页模板缺失的问题
     */
    @Test
    public void shouldKeepMermaidViewerToolbarUsingCompactInlineSvgIcons() throws IOException {
        String defaultHtml = readProjectFile(MERMAID_PREVIEW_VIEWER_PATH);
        String escapedZoomInIconMarker = "data-mermaid-viewer-icon=\\\"zoom-in\\\"";
        String escapedZoomOutIconMarker = "data-mermaid-viewer-icon=\\\"zoom-out\\\"";
        String escapedResetIconMarker = "data-mermaid-viewer-icon=\\\"reset\\\"";
        String escapedCloseIconMarker = "data-mermaid-viewer-icon=\\\"close\\\"";

        Assert.assertTrue("查看层工具栏按钮应统一使用内联 SVG 图标尺寸",
                defaultHtml.contains(".markdown-preview-figure-viewer__button svg"));
        Assert.assertTrue("查看层按钮应使用紧凑固定宽度",
                defaultHtml.contains("width: 32px;"));
        Assert.assertTrue("查看层按钮应使用紧凑固定高度",
                defaultHtml.contains("height: 32px;"));
        Assert.assertTrue("查看层工具栏应包含放大镜加号图标",
                defaultHtml.contains(escapedZoomInIconMarker));
        Assert.assertTrue("查看层工具栏应包含放大镜减号图标",
                defaultHtml.contains(escapedZoomOutIconMarker));
        Assert.assertTrue("查看层工具栏应包含重置图标",
                defaultHtml.contains(escapedResetIconMarker));
        Assert.assertTrue("查看层工具栏应包含关闭图标",
                defaultHtml.contains(escapedCloseIconMarker));
    }

    /**
     * 验证仓库里保留了一组可重复使用的 Mermaid 升级验收样例。
     * 该测试约束样例覆盖 8 个 Mermaid 代码块，并同时包含旧语法常见场景、升级后重点关注的图表类型，
     * 以及一组更适合放大查看回归的“大尺寸图表”样例和两组标签换行样例，
     * 这样后续继续升级时，可以直接拿同一份 Markdown 做预览、HTML 导出和 PDF 导出的手工回归。
     *
     * @throws IOException 当样例资源读取失败时抛出，用于暴露回归基线缺失的问题
     */
    @Test
    public void shouldProvideStableMermaidUpgradeBaselineSamples() throws IOException {
        String baselineMarkdown = readProjectFile(MERMAID_BASELINE_PATH);
        int mermaidFenceCount = countOccurrences(baselineMarkdown, "```mermaid");

        Assert.assertEquals("升级验收基线应固定为 8 组 Mermaid 样例，便于人工回归复用", 8, mermaidFenceCount);
        Assert.assertTrue("基线样例应覆盖传统 flowchart 语法",
                baselineMarkdown.contains("flowchart TD"));
        Assert.assertTrue("基线样例应覆盖传统 sequenceDiagram 语法",
                baselineMarkdown.contains("sequenceDiagram"));
        Assert.assertTrue("基线样例应覆盖传统 gantt 语法",
                baselineMarkdown.contains("gantt"));
        Assert.assertTrue("基线样例应覆盖升级后重点关注的 mindmap 场景",
                baselineMarkdown.contains("mindmap"));
        Assert.assertTrue("基线样例应覆盖升级后重点关注的 quadrantChart 场景",
                baselineMarkdown.contains("quadrantChart"));
        Assert.assertTrue("基线样例应覆盖更适合放大查看回归的宽图 Mermaid 场景",
                baselineMarkdown.contains("Zoom Stress Flowchart"));
        Assert.assertTrue("基线样例应覆盖状态图里的换行转移标签",
                baselineMarkdown.contains("stateDiagram-v2")
                        && baselineMarkdown.contains("点击搜索区\\n(A 组)"));
        Assert.assertTrue("基线样例应覆盖模块图里的多行节点和无空格边标签",
                baselineMarkdown.contains("flowchart TB")
                        && baselineMarkdown.contains("fragment_gc_games_lobby.xml")
                        && baselineMarkdown.contains("ISearchPageService.openSearch")
                        && baselineMarkdown.contains("IDetailPageService.jumpPlayActivity"));
    }

    /**
     * 验证标签换行修复同时写进预览脚本和导出脚本。
     * 只改未压缩的 index.js 时，JCEF 仍会加载 index.min.js，导出仍会走 method.min.js。
     *
     * @throws IOException 当模板或打包脚本读取失败时抛出
     */
    @Test
    public void shouldKeepMermaidLabelWrapFixesInPreviewAndExport() throws IOException {
        String defaultHtml = readProjectFile(DEFAULT_HTML_PATH);
        String indexMinJs = readProjectFile(VDITOR_INDEX_MIN_JS_PATH);
        String methodMinJs = readProjectFile(VDITOR_METHOD_MIN_JS_PATH);

        Assert.assertTrue("预览模板应在测量节点和最终图块上取消 Mermaid 的 nowrap 宽度上限",
                defaultHtml.contains("div[id^=\"dmermaid\"]")
                        && defaultHtml.contains("white-space: pre-line !important;")
                        && defaultHtml.contains("max-width: none !important;"));
        Assert.assertTrue("预览脚本应使用全局 htmlLabels，并把 flowchart.wrappingWidth 写成 480",
                indexMinJs.contains(MERMAID_HTML_LABEL_CONFIG));
        Assert.assertTrue("导出脚本应使用同一套 htmlLabels 和 wrappingWidth",
                methodMinJs.contains(MERMAID_HTML_LABEL_CONFIG));
        Assert.assertTrue("预览脚本应在渲染前把 Mermaid 源码里的反斜杠 n 换成 br",
                indexMinJs.contains(MERMAID_BACKSLASH_N_REPLACE));
        Assert.assertTrue("导出脚本应在渲染前做同样的反斜杠 n 替换",
                methodMinJs.contains(MERMAID_BACKSLASH_N_REPLACE));
        Assert.assertTrue("放大查看仍应克隆当前 SVG，而不是另画一套图",
                defaultHtml.contains("svgElement.cloneNode(true)"));
    }

    /**
     * 以 UTF-8 读取项目内文本文件。
     * 这里显式固定编码，是为了避免在包含中文注释和 Markdown 文本的文件上再次引入平台相关乱码干扰。
     *
     * @param filePath 需要读取的项目内相对路径
     * @return 文件的完整文本内容
     * @throws IOException 当文件不存在或读取失败时抛出，交由调用方测试直接失败
     */
    private static String readProjectFile(Path filePath) throws IOException {
        return Files.readString(filePath, StandardCharsets.UTF_8);
    }

    /**
     * 统计指定文本片段在目标字符串中的出现次数。
     * 该方法用于稳定计算 Mermaid 代码块数量，避免依赖正则导致样例文本调整后难以维护。
     *
     * @param text 目标完整文本
     * @param fragment 需要统计的固定片段
     * @return 片段在目标文本中的出现次数
     */
    private static int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(fragment, index)) >= 0) {
            count++;
            index += fragment.length();
        }
        return count;
    }
}
