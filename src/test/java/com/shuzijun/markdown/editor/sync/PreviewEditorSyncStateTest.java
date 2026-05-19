package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 预览联动状态对象测试。
 * 这些测试聚焦宿主侧最核心的同步规则，确保内容版本、挂起命令、激活定位优先级以及回环抑制窗口
 * 在后续接入 IntelliJ 编辑器监听和 JCEF 页面桥时具备稳定、可回归的行为约束。
 */
public class PreviewEditorSyncStateTest {

    /**
     * 验证挂起的 reveal 命令只有在预览页已经完成当前内容版本渲染后才能被消费。
     * 这可以避免“宿主先发滚动定位、预览页还停留在旧内容”时出现定位漂移。
     */
    @Test
    public void shouldRequireRenderedCurrentVersionBeforeConsumingPendingRevealCommand() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        // 先声明宿主当前内容已经推进到版本 3，并缓存一条等待发送到预览页的定位命令。
        state.markContentChanged(3L);
        state.markPreviewReady();
        state.updateEditorAnchor(18, 0.35d);
        state.setPendingRevealCommand(new PreviewEditorSyncState.PendingRevealCommand(18, 0.35d, 3L, "caret"));

        // 预览页尚未渲染到当前版本时，不允许取出该命令，避免旧 DOM 上错误滚动。
        Assert.assertNull(state.consumePendingRevealCommandIfReady());

        // 只有当预览页回报已渲染当前版本后，挂起命令才会被释放。
        state.markPreviewRendered(3L);
        PreviewEditorSyncState.PendingRevealCommand command = state.consumePendingRevealCommandIfReady();
        Assert.assertNotNull(command);
        Assert.assertEquals(18, command.getLine());
        Assert.assertEquals(0.35d, command.getTopRatio(), 0.0001d);
        Assert.assertEquals("caret", command.getReason());

        // 命令是一次性消费的，避免同一条程序性滚动被重复执行。
        Assert.assertNull(state.consumePendingRevealCommandIfReady());
    }

    /**
     * 验证源码编辑器锚点会被独立记录，供切换到预览页时恢复最近一次源码视口位置。
     */
    @Test
    public void shouldStoreEditorAnchorForPreviewActivation() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updateEditorAnchor(27, 0.62d);

        Assert.assertEquals(27, state.getEditorAnchorLine());
        Assert.assertEquals(0.62d, state.getEditorTopRatio(), 0.0001d);
    }

    /**
     * 验证从预览页切回源码编辑器时，若最近一次存在有效选区，应优先按选区起始行定位；
     * 若没有有效选区，则退化为最近一次预览视口锚点行。
     */
    @Test
    public void shouldPreferPreviewSelectionStartLineWhenResolvingSourceActivationTarget() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        // 先写入一个普通预览锚点，模拟用户仅滚动过预览页但没有做选区。
        state.updatePreviewAnchor(42, "block-42");
        Assert.assertEquals(42, state.resolveSourceActivationTargetLine());

        // 再写入选区信息后，应优先返回选区起始行，而不是普通锚点。
        state.updatePreviewSelection(11, 16, "selected text");
        Assert.assertEquals(11, state.resolveSourceActivationTargetLine());

        // 清空选区后，应重新退化为普通锚点行。
        state.clearPreviewSelection();
        Assert.assertEquals(42, state.resolveSourceActivationTargetLine());
    }

    /**
     * 验证宿主和预览双向抑制窗口的时间判定逻辑。
     * 该逻辑直接决定是否会在程序性滚动后误把回写事件当作用户操作再次触发联动回环。
     */
    @Test
    public void shouldRespectSuppressionWindows() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        // 分别设置两个方向的抑制窗口，并验证窗口内外的命中情况。
        state.suppressEditorEventsUntil(1200L);
        state.suppressPreviewEventsUntil(2400L);

        Assert.assertTrue(state.isEditorEventSuppressed(1199L));
        Assert.assertFalse(state.isEditorEventSuppressed(1200L));

        Assert.assertTrue(state.isPreviewEventSuppressed(2399L));
        Assert.assertFalse(state.isPreviewEventSuppressed(2400L));
    }

    /**
     * 验证预览页已渲染版本号的更新规则。
     * 较旧的 rendered 回报不应回退当前状态，否则会导致宿主误判预览页重新落后。
     */
    @Test
    public void shouldKeepNewestRenderedVersion() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.markPreviewRendered(8L);
        state.markPreviewRendered(5L);

        Assert.assertEquals(8L, state.getPreviewRenderedVersion());
    }

    /**
     * 验证预览页生命周期重置只清空页面实例强相关状态，而不会丢失最近一次可用于恢复的语义锚点。
     * 这对应强制刷新、白屏重建或页面尚未 ready 时的恢复场景。
     */
    @Test
    public void shouldResetPreviewLifecycleWithoutDroppingSemanticAnchors() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.markContentChanged(10L);
        state.markPreviewReady();
        state.markPreviewRendered(10L);
        state.updatePreviewAnchor(33, "block-33");
        state.updatePreviewSelection(31, 35, "preview selection");
        state.setPendingRevealCommand(new PreviewEditorSyncState.PendingRevealCommand(33, 0.5d, 10L, "activation"));

        state.resetPreviewLifecycle();

        Assert.assertFalse(state.isPreviewReady());
        Assert.assertEquals(0L, state.getPreviewRenderedVersion());
        Assert.assertNull(state.consumePendingRevealCommandIfReady());
        Assert.assertEquals(31, state.resolveSourceActivationTargetLine());
        Assert.assertEquals(10L, state.getContentVersion());
    }
}
