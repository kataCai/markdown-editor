package com.shuzijun.markdown.editor.sync;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 预览联动宿主侧状态对象。
 * 该对象只保存 Java 宿主为联动决策所需的最小上下文，不缓存预览页完整 source map，
 * 从而降低内容版本失配时的陈旧状态风险。
 * 适用场景包括：
 * 1. 源码编辑器滚动/光标变更后向预览页发起 reveal。
 * 2. 预览页滚动/选区变化后回写源码编辑器定位。
 * 3. 预览页尚未 ready 或尚未渲染到当前内容版本时，临时挂起待发送命令。
 */
public class PreviewEditorSyncState {

    /**
     * 宿主侧短窗去重的时间窗口。
     * `caret` 与 `visibleArea` 往往会在同一用户动作后紧挨着触发，这里用短窗合并重复 reveal。
     */
    private static final long EDITOR_REVEAL_DEDUP_WINDOW_MS = 120L;

    /**
     * 宿主侧短窗去重的相对位置容差。
     * 允许可视区换算带来的细小浮动，但不会吞掉真实滚动导致的明显位置变化。
     */
    private static final double EDITOR_REVEAL_TOP_RATIO_TOLERANCE = 0.03d;

    /**
     * 预览输入参与“切回源码”激活恢复的最长有效时间窗。
     * 这里只允许消费最近一小段时间内产生的预览锚点或预览选区，避免旧页面、旧选区或程序性事件在后续切换时被误当成最新用户意图。
     */
    private static final long ACTIVATION_INPUT_EXPIRE_WINDOW_MS = 2000L;

    private long contentVersion;
    private long previewRenderedVersion;
    private boolean previewReady;
    private int editorAnchorLine = -1;
    private double editorTopRatio;

    private int previewAnchorLine = -1;
    private String previewAnchorSourceId;
    private PreviewAnchorKind previewAnchorKind = PreviewAnchorKind.USER_SCROLL;
    private long previewAnchorUpdatedAt;

    private int previewSelectionStartLine = -1;
    private int previewSelectionEndLine = -1;
    private String previewSelectionPreviewText;
    private long previewSelectionUpdatedAt;

    private long suppressEditorEventUntil;
    private long suppressPreviewEventUntil;
    private boolean previewDirty;
    private int lastDispatchedEditorRevealLine = -1;
    private double lastDispatchedEditorRevealTopRatio = -1d;
    private long lastDispatchedEditorRevealAt;
    private int lastRestoredSourceLine = -1;
    private long lastRestoredSourceAt;

    private PendingRevealCommand pendingRevealCommand;

    /**
     * 标记宿主源码内容已推进到新的版本号。
     * 该方法不会主动清空挂起命令，因为同版本命令仍可能在预览页补齐渲染后继续生效。
     *
     * @param newContentVersion 最新源码内容版本
     */
    public void markContentChanged(long newContentVersion) {
        contentVersion = Math.max(contentVersion, newContentVersion);
    }

    /**
     * 更新最近一次来自源码编辑器的视口/光标锚点。
     * 该锚点用于在切换到预览页时恢复最近一次源码浏览语义位置。
     *
     * @param anchorLine 最近一次有效源码锚点行
     * @param topRatio   该锚点在当前可视区中的相对高度比例
     */
    public void updateEditorAnchor(int anchorLine, double topRatio) {
        editorAnchorLine = anchorLine;
        editorTopRatio = topRatio;
    }

    /**
     * 标记预览页已完成初始化并可接收宿主同步指令。
     * ready 只代表通信链路可用，不代表当前内容版本已同步。
     */
    public void markPreviewReady() {
        previewReady = true;
    }

    /**
     * 重置当前预览页的生命周期状态。
     * 该方法用于 JCEF 预览页被重建、强制刷新或尚未 ready 的阶段，只清空与页面实例强相关的状态，
     * 保留最近一次源码/预览锚点，便于新页面 ready 后继续恢复对齐。
     */
    public void resetPreviewLifecycle() {
        previewReady = false;
        previewRenderedVersion = 0L;
        pendingRevealCommand = null;
        previewDirty = false;
        lastDispatchedEditorRevealLine = -1;
        lastDispatchedEditorRevealTopRatio = -1d;
        lastDispatchedEditorRevealAt = 0L;
    }

    /**
     * 标记预览页已经渲染到指定内容版本。
     * 为避免乱序消息回退状态，这里只接受更“新”的版本号。
     *
     * @param renderedVersion 预览页回报已完成渲染的内容版本
     */
    public void markPreviewRendered(long renderedVersion) {
        previewRenderedVersion = Math.max(previewRenderedVersion, renderedVersion);
    }

    /**
     * 设置一条等待发送到预览页的 reveal 指令。
     * 该命令通常发生在预览页未 ready、尚未渲染到当前版本，或宿主需要延迟补发滚动对齐时。
     *
     * @param command 待发送的 reveal 指令；传入 {@code null} 表示清空
     */
    public void setPendingRevealCommand(@Nullable PendingRevealCommand command) {
        pendingRevealCommand = command;
    }

    /**
     * 在预览页 ready 且已渲染到挂起命令所需内容版本时，消费该命令。
     * 消费后会立即清空挂起状态，避免同一条程序性定位重复执行。
     *
     * @return 满足发送条件时返回待执行命令，否则返回 {@code null}
     */
    @Nullable
    public PendingRevealCommand consumePendingRevealCommandIfReady() {
        if (pendingRevealCommand == null) {
            return null;
        }
        if (!previewReady || previewRenderedVersion < pendingRevealCommand.getRequiredVersion()) {
            return null;
        }
        PendingRevealCommand command = pendingRevealCommand;
        pendingRevealCommand = null;
        return command;
    }

    /**
     * 更新最近一次来自预览视口的语义锚点。
     * 该锚点在没有更强语义（如预览选区）时，用于切回源码编辑器后的默认定位。
     *
     * @param anchorLine 当前预览视口反推出的源码锚点行
     * @param sourceId   对应的块级锚点标识，可为空
     */
    public void updatePreviewAnchor(int anchorLine, @Nullable String sourceId) {
        updatePreviewAnchor(anchorLine, sourceId, PreviewAnchorKind.USER_SCROLL);
    }

    /**
     * 更新最近一次来自预览视口的语义锚点，并附带锚点有效性类型。
     * 该类型用于区分用户真实滚动与无滚动场景下的伪锚点，避免激活切换时错误消费无效锚点。
     *
     * @param anchorLine 当前预览视口反推出的源码锚点行
     * @param sourceId   对应的块级锚点标识，可为空
     * @param anchorKind 预览锚点类型
     */
    public void updatePreviewAnchor(int anchorLine, @Nullable String sourceId, @NotNull PreviewAnchorKind anchorKind) {
        updatePreviewAnchor(anchorLine, sourceId, anchorKind, System.currentTimeMillis());
    }

    /**
     * 更新最近一次来自预览视口的语义锚点，并显式写入该锚点的产生时间。
     * 该重载主要用于测试或需要外部精确控制事件时间线的场景；常规业务代码应优先使用自动取当前时间的重载。
     *
     * @param anchorLine      当前预览视口反推出来的源码锚点行
     * @param sourceId        对应的块级锚点标识，可为空
     * @param anchorKind      预览锚点类型
     * @param updatedAtMillis 锚点被宿主接收时的时间戳，用于后续激活恢复时的过期判定
     */
    public void updatePreviewAnchor(int anchorLine,
                                    @Nullable String sourceId,
                                    @NotNull PreviewAnchorKind anchorKind,
                                    long updatedAtMillis) {
        previewAnchorLine = anchorLine;
        previewAnchorSourceId = sourceId;
        previewAnchorKind = anchorKind;
        previewAnchorUpdatedAt = updatedAtMillis;
    }

    /**
     * 更新最近一次来自预览页的选区定位信息。
     * 预览选区优先级高于普通视口锚点，用于“从预览回到源码”时尽量恢复用户正在操作的区间。
     *
     * @param startLine           选区起始源码行
     * @param endLine             选区结束源码行
     * @param selectedTextPreview 选中文本摘要，可为空
     */
    public void updatePreviewSelection(int startLine, int endLine, @Nullable String selectedTextPreview) {
        updatePreviewSelection(startLine, endLine, selectedTextPreview, System.currentTimeMillis());
    }

    /**
     * 更新最近一次来自预览页的选区定位信息，并显式写入该选区的产生时间。
     * 该时间戳用于约束选区只在短时间内参与“切回源码”的自动恢复，避免旧选区长期滞留后跨多次切换反复生效。
     *
     * @param startLine           选区起始源码行
     * @param endLine             选区结束源码行
     * @param selectedTextPreview 选中文本摘要，可为空
     * @param updatedAtMillis     选区被宿主接收时的时间戳，用于激活恢复的过期判定
     */
    public void updatePreviewSelection(int startLine,
                                       int endLine,
                                       @Nullable String selectedTextPreview,
                                       long updatedAtMillis) {
        previewSelectionStartLine = startLine;
        previewSelectionEndLine = endLine;
        previewSelectionPreviewText = selectedTextPreview;
        previewSelectionUpdatedAt = updatedAtMillis;
    }

    /**
     * 清空最近一次预览选区信息。
     * 当选区失效、页面刷新重建或宿主确认无需再按区间恢复定位时调用。
     */
    public void clearPreviewSelection() {
        previewSelectionStartLine = -1;
        previewSelectionEndLine = -1;
        previewSelectionPreviewText = null;
        previewSelectionUpdatedAt = 0L;
    }

    /**
     * 解析“切回源码编辑器”时优先跳转的目标行。
     * 若存在有效预览选区，则优先返回选区起始行；否则退化为普通视口锚点行。
     *
     * @return 可用目标行；若当前没有任何有效锚点则返回 {@code -1}
     */
    public int resolveSourceActivationTargetLine() {
        return resolveSourceActivationTargetLine(System.currentTimeMillis());
    }

    /**
     * 按给定时间点解析“切回源码编辑器”时优先跳转的目标行。
     * 解析顺序仍然保持“稳定预览选区优先、普通预览视口锚点兜底”，但两者都必须在有效时间窗内，超时后直接视为无效输入。
     *
     * @param nowMillis 当前用于判定输入是否过期的时间戳
     * @return 可用目标行；若当前没有任何仍然有效的预览输入则返回 {@code -1}
     */
    public int resolveSourceActivationTargetLine(long nowMillis) {
        if (previewSelectionStartLine >= 0 && !isActivationInputExpired(previewSelectionUpdatedAt, nowMillis)) {
            return previewSelectionStartLine;
        }
        if (!previewAnchorKind.isEligibleForActivationRestore()) {
            return -1;
        }
        if (previewAnchorLine < 0 || isActivationInputExpired(previewAnchorUpdatedAt, nowMillis)) {
            return -1;
        }
        return previewAnchorLine;
    }

    /**
     * 判断某条预览输入是否已经超过可参与激活恢复的有效时间窗。
     * 当时间戳缺失、异常倒退或距离当前时间过久时，都按过期处理，优先保证不会消费陈旧状态。
     *
     * @param updatedAtMillis 输入最近一次被宿主接收时的时间戳
     * @param nowMillis       当前判定时刻
     * @return {@code true} 表示该预览输入不应继续参与激活恢复
     */
    private boolean isActivationInputExpired(long updatedAtMillis, long nowMillis) {
        if (updatedAtMillis <= 0L) {
            return true;
        }
        if (nowMillis < updatedAtMillis) {
            return false;
        }
        return nowMillis - updatedAtMillis > ACTIVATION_INPUT_EXPIRE_WINDOW_MS;
    }

    /**
     * 设置源码编辑器方向的事件抑制窗口结束时间。
     * 用于在预览页程序性回写源码滚动后，短时间内忽略由 IDE 自身产生的联动回声事件。
     *
     * @param timestampMillis 抑制截止时间戳；当当前时间小于该值时视为仍在抑制窗口内
     */
    public void suppressEditorEventsUntil(long timestampMillis) {
        suppressEditorEventUntil = timestampMillis;
    }

    /**
     * 设置预览页方向的事件抑制窗口结束时间。
     * 用于在宿主程序性驱动预览滚动后，短时间内忽略预览页回报的联动回声事件。
     *
     * @param timestampMillis 抑制截止时间戳；当当前时间小于该值时视为仍在抑制窗口内
     */
    public void suppressPreviewEventsUntil(long timestampMillis) {
        suppressPreviewEventUntil = timestampMillis;
    }

    /**
     * 判断当前源码编辑器事件是否仍处于抑制窗口内。
     *
     * @param nowMillis 当前时间戳
     * @return {@code true} 表示应忽略该次源码编辑器事件
     */
    public boolean isEditorEventSuppressed(long nowMillis) {
        return nowMillis < suppressEditorEventUntil;
    }

    /**
     * 判断当前预览页事件是否仍处于抑制窗口内。
     *
     * @param nowMillis 当前时间戳
     * @return {@code true} 表示应忽略该次预览页事件
     */
    public boolean isPreviewEventSuppressed(long nowMillis) {
        return nowMillis < suppressPreviewEventUntil;
    }

    /**
     * 更新预览页是否存在本地未保存修改。
     * 当预览页处于脏状态时，宿主应暂停主动下发内容覆盖和程序性 reveal，避免覆盖页面中的本地编辑。
     *
     * @param dirty {@code true} 表示预览页存在未保存本地修改
     */
    public void setPreviewDirty(boolean dirty) {
        previewDirty = dirty;
    }

    /**
     * 判断当前源码侧 reveal 是否与短时间内刚发送过的 reveal 属于同一语义位置。
     * 该规则主要用于合并“光标变化”和“可视区变化”在同一用户动作后形成的重复联动消息。
     *
     * @param line      本次待发送的目标逻辑行
     * @param topRatio  本次待发送的相对可视区位置
     * @param nowMillis 当前时间戳
     * @return {@code true} 表示应跳过这次 reveal，避免重复联动
     */
    public boolean shouldSkipDuplicatedEditorReveal(int line, double topRatio, long nowMillis) {
        if (lastDispatchedEditorRevealLine < 0) {
            return false;
        }
        if (nowMillis - lastDispatchedEditorRevealAt > EDITOR_REVEAL_DEDUP_WINDOW_MS) {
            return false;
        }
        return lastDispatchedEditorRevealLine == line
                && Math.abs(lastDispatchedEditorRevealTopRatio - topRatio) <= EDITOR_REVEAL_TOP_RATIO_TOLERANCE;
    }

    /**
     * 记录最近一次已接受的源码侧 reveal。
     * 该记录只服务于短窗去重，不改变内容版本、锚点或挂起命令的生命周期。
     *
     * @param line      最近一次发送的目标逻辑行
     * @param topRatio  最近一次发送的相对可视区位置
     * @param nowMillis 发送时间戳
     */
    public void recordEditorRevealDispatch(int line, double topRatio, long nowMillis) {
        lastDispatchedEditorRevealLine = line;
        lastDispatchedEditorRevealTopRatio = topRatio;
        lastDispatchedEditorRevealAt = nowMillis;
    }

    /**
     * 记录最近一次“预览回写到源码编辑器”的实际恢复动作。
     * 该记录只服务于激活切换场景的 no-op 去重，不改变预览锚点、选区或挂起命令状态。
     *
     * @param line      最近一次实际恢复到的目标逻辑行号
     * @param nowMillis 执行恢复时的时间戳
     */
    public void recordSourceRestore(int line, long nowMillis) {
        lastRestoredSourceLine = line;
        lastRestoredSourceAt = nowMillis;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public long getPreviewRenderedVersion() {
        return previewRenderedVersion;
    }

    public boolean isPreviewReady() {
        return previewReady;
    }

    public int getEditorAnchorLine() {
        return editorAnchorLine;
    }

    public double getEditorTopRatio() {
        return editorTopRatio;
    }

    public boolean isPreviewDirty() {
        return previewDirty;
    }

    public int getPreviewAnchorLine() {
        return previewAnchorLine;
    }

    @Nullable
    public String getPreviewAnchorSourceId() {
        return previewAnchorSourceId;
    }

    @NotNull
    public PreviewAnchorKind getPreviewAnchorKind() {
        return previewAnchorKind;
    }

    public int getPreviewSelectionStartLine() {
        return previewSelectionStartLine;
    }

    public int getPreviewSelectionEndLine() {
        return previewSelectionEndLine;
    }

    @Nullable
    public String getPreviewSelectionPreviewText() {
        return previewSelectionPreviewText;
    }

    /**
     * 读取最近一次实际执行的源码恢复目标行号。
     * 该值仅用于激活切换时的 no-op 判断，不代表当前源码光标或当前预览锚点。
     *
     * @return 最近一次实际恢复到的目标逻辑行号；若尚未发生恢复则返回负数
     */
    public int getLastRestoredSourceLine() {
        return lastRestoredSourceLine;
    }

    /**
     * 读取最近一次实际执行源码恢复的时间戳。
     * 该时间戳用于识别“同一目标在极短时间内被重复恢复”的抖动场景。
     *
     * @return 最近一次执行源码恢复的时间戳；若尚未发生恢复则返回 0
     */
    public long getLastRestoredSourceAt() {
        return lastRestoredSourceAt;
    }

    /**
     * 挂起的 reveal 指令。
     * 该对象只描述一次最小滚动对齐请求，不承担复杂队列职责；后续若需要合并策略，应在协调器层处理。
     */
    public static class PendingRevealCommand {

        private final int line;
        private final double topRatio;
        private final long requiredVersion;
        private final String reason;

        /**
         * 创建一条待发送到预览页的 reveal 指令。
         *
         * @param line            目标源码行
         * @param topRatio        目标行在可视区内的相对位置比例
         * @param requiredVersion 该指令依赖的内容版本；预览未渲染到该版本前不能执行
         * @param reason          指令来源原因，例如 caret、scroll、activation
         */
        public PendingRevealCommand(int line, double topRatio, long requiredVersion, String reason) {
            this.line = line;
            this.topRatio = topRatio;
            this.requiredVersion = requiredVersion;
            this.reason = reason;
        }

        public int getLine() {
            return line;
        }

        public double getTopRatio() {
            return topRatio;
        }

        public long getRequiredVersion() {
            return requiredVersion;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 预览锚点类型。
     * 该类型用于区分哪些锚点可以在切回源码编辑器时参与自动恢复定位。
     */
    public enum PreviewAnchorKind {
        USER_SCROLL(true),
        USER_SELECTION(true),
        PROGRAMMATIC_REVEAL(false),
        ACTIVATION_SYNC(false),
        NO_SCROLL_SYNTHETIC(false);

        private final boolean eligibleForActivationRestore;

        PreviewAnchorKind(boolean eligibleForActivationRestore) {
            this.eligibleForActivationRestore = eligibleForActivationRestore;
        }

        public boolean isEligibleForActivationRestore() {
            return eligibleForActivationRestore;
        }
    }
}
