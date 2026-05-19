package com.shuzijun.markdown.editor.sync;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 预览联动宿主侧协调器。
 * 该协调器负责把文档内容版本、预览页生命周期状态和 reveal 挂起/补发逻辑集中管理，
 * 避免这些关键判断散落在 `MarkdownPreviewFileEditor` 或 `MarkdownHtmlPanel` 中。
 * 当前阶段主要覆盖三类职责：
 * 1. 在预览页 ready 或源码文档变更后决定何时推送 applyMarkdown。
 * 2. 在预览页 rendered 之后补发此前挂起的 reveal 指令。
 * 3. 对外暴露统一的 `PreviewSyncMessage` 列表，供宿主 UI 层逐条下发到页面。
 */
public class PreviewEditorSyncCoordinator {

    private final String filePath;
    private final PreviewEditorSyncState state = new PreviewEditorSyncState();

    private String currentMarkdown = "";
    private List<MarkdownSourceMapParser.BlockMapping> currentBlockMappings = Collections.emptyList();

    /**
     * 创建指定文件对应的同步协调器。
     *
     * @param filePath 当前 Markdown 文档绝对路径
     */
    public PreviewEditorSyncCoordinator(@NotNull String filePath) {
        this.filePath = filePath;
    }

    /**
     * 更新宿主当前持有的 Markdown 文本和内容版本。
     * 若预览页已经 ready，则立即返回一条 applyMarkdown 消息，让页面尽快追平到最新版本。
     *
     * @param markdown       最新 Markdown 文本
     * @param contentVersion 最新内容版本
     * @return 需要立即发送给预览页的结构化消息列表
     */
    @NotNull
    public List<PreviewSyncMessage> updateDocument(@NotNull String markdown, long contentVersion) {
        currentMarkdown = markdown;
        currentBlockMappings = MarkdownSourceMapParser.parse(markdown);
        state.markContentChanged(contentVersion);
        if (!state.isPreviewReady() || state.isPreviewDirty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(buildApplyMarkdownMessage("programmatic", "documentChanged"));
    }

    /**
     * 处理预览页 ready 事件。
     * 新页面 ready 后需要立即收到当前 Markdown 基线内容，否则后续 rendered / reveal 链路无法建立。
     *
     * @return 需要发送给预览页的初始化同步消息列表
     */
    @NotNull
    public List<PreviewSyncMessage> handlePreviewReady() {
        state.markPreviewReady();
        return Collections.singletonList(buildApplyMarkdownMessage("activation", "previewReady"));
    }

    /**
     * 处理预览页 rendered 事件。
     * 该事件除了推进 renderedVersion，还需要检查是否存在此前因版本落后而挂起的 reveal 指令。
     *
     * @param renderedVersion 预览页回报已渲染完成的内容版本
     * @return 若存在可补发的 reveal 指令，则返回对应消息；否则返回空列表
     */
    @NotNull
    public List<PreviewSyncMessage> handlePreviewRendered(long renderedVersion) {
        state.markPreviewRendered(renderedVersion);
        PreviewEditorSyncState.PendingRevealCommand command = state.consumePendingRevealCommandIfReady();
        if (command == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                PreviewSyncMessage.revealSourceLine(
                        filePath,
                        state.getContentVersion(),
                        "programmatic",
                        command.getLine(),
                        command.getTopRatio(),
                        command.getReason()
                )
        );
    }

    /**
     * 请求将预览页滚动到某条源码语义行附近。
     * 若预览页尚未 ready 或尚未渲染到当前内容版本，则先挂起；否则立即返回 reveal 指令。
     *
     * @param line     目标源码行
     * @param topRatio 目标行在可视区中的相对位置比例
     * @param reason   触发原因，例如 caret、scroll、activation
     * @return 需要立即发送的消息列表；若暂不可发送则返回空列表
     */
    @NotNull
    public List<PreviewSyncMessage> requestRevealSourceLine(int line, double topRatio, @NotNull String reason) {
        if (state.isPreviewDirty()) {
            return Collections.emptyList();
        }
        state.setPendingRevealCommand(new PreviewEditorSyncState.PendingRevealCommand(
                line,
                topRatio,
                state.getContentVersion(),
                reason
        ));
        PreviewEditorSyncState.PendingRevealCommand command = state.consumePendingRevealCommandIfReady();
        if (command == null) {
            return Collections.emptyList();
        }
        List<PreviewSyncMessage> result = new ArrayList<>(1);
        result.add(PreviewSyncMessage.revealSourceLine(
                filePath,
                state.getContentVersion(),
                "programmatic",
                command.getLine(),
                command.getTopRatio(),
                command.getReason()
        ));
        return result;
    }

    /**
     * 在预览页被重建、强制刷新或失效后重置页面生命周期状态。
     * 这样新的页面实例可以重新经历 ready -> rendered 的内容同步流程，同时保留语义锚点供恢复定位。
     */
    public void resetPreviewLifecycle() {
        state.resetPreviewLifecycle();
    }

    /**
     * 处理预览页本地脏状态变化。
     * 当预览页存在未保存本地编辑时，宿主暂停主动覆盖页面内容；脏状态结束后恢复正常版本同步。
     *
     * @param dirty {@code true} 表示预览页存在未保存本地修改
     */
    public void handlePreviewDirtyChanged(boolean dirty) {
        state.setPreviewDirty(dirty);
    }

    /**
     * 返回内部状态对象，供 UI 层继续复用同一份锚点和抑制窗口上下文。
     * 当前返回的是协调器自持有的唯一实例，调用方不应随意替换引用。
     *
     * @return 当前协调器持有的同步状态对象
     */
    @NotNull
    public PreviewEditorSyncState getState() {
        return state;
    }

    @NotNull
    private PreviewSyncMessage buildApplyMarkdownMessage(@NotNull String source, @NotNull String reason) {
        return PreviewSyncMessage.applyMarkdown(
                filePath,
                state.getContentVersion(),
                source,
                currentMarkdown,
                reason,
                currentBlockMappings
        );
    }
}
