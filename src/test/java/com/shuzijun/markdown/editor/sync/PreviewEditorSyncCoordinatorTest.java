package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * 预览联动协调器测试。
 * 测试目标是把“源码内容版本同步”和“渲染完成后补发 reveal”这两个最容易写散的流程
 * 固定为可回归的宿主侧规则，避免这些状态判断直接散落到 UI 类中。
 */
public class PreviewEditorSyncCoordinatorTest {

    /**
     * 验证预览页 ready 后会先收到当前 Markdown 内容，而不是直接等待下一次文档变更。
     * 这是新建页面、强制刷新和白屏恢复后重新建立内容版本基线的前置条件。
     */
    @Test
    public void shouldSendCurrentMarkdownWhenPreviewBecomesReady() {
        PreviewEditorSyncCoordinator coordinator = new PreviewEditorSyncCoordinator("E:/demo.md");

        coordinator.updateDocument("# Title\n", 3L);

        List<PreviewSyncMessage> messages = coordinator.handlePreviewReady();

        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(PreviewSyncMessage.TYPE_APPLY_MARKDOWN, messages.get(0).getType());
        Assert.assertEquals(3L, messages.get(0).getContentVersion());
        Assert.assertEquals("# Title\n", messages.get(0).getPayload().getString("markdown"));
    }

    /**
     * 验证 reveal 指令在预览页尚未渲染到当前版本时会先挂起，
     * 直到收到对应 rendered 回报后再补发，避免滚动发生在旧 DOM 上。
     */
    @Test
    public void shouldQueueRevealUntilPreviewRenderedCurrentVersion() {
        PreviewEditorSyncCoordinator coordinator = new PreviewEditorSyncCoordinator("E:/demo.md");

        coordinator.updateDocument("# Title\n", 5L);
        coordinator.handlePreviewReady();

        List<PreviewSyncMessage> beforeRendered = coordinator.requestRevealSourceLine(20, 0.4d, "caret");
        Assert.assertTrue(beforeRendered.isEmpty());

        List<PreviewSyncMessage> afterRendered = coordinator.handlePreviewRendered(5L);
        Assert.assertEquals(1, afterRendered.size());
        Assert.assertEquals(PreviewSyncMessage.TYPE_REVEAL_SOURCE_LINE, afterRendered.get(0).getType());
        Assert.assertEquals(20, afterRendered.get(0).getPayload().getIntValue("line"));
    }

    /**
     * 验证文档内容变化时，如果预览页已经 ready，则会立即推送新的 Markdown 版本。
     * 该行为是后续滚动映射稳定性的基础，因为所有定位都依赖预览页已消费当前内容版本。
     */
    @Test
    public void shouldPushMarkdownImmediatelyWhenDocumentChangesAfterPreviewReady() {
        PreviewEditorSyncCoordinator coordinator = new PreviewEditorSyncCoordinator("E:/demo.md");

        coordinator.updateDocument("# Title\n", 1L);
        coordinator.handlePreviewReady();
        coordinator.handlePreviewRendered(1L);

        List<PreviewSyncMessage> messages = coordinator.updateDocument("# Title\n\nnext\n", 2L);

        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(PreviewSyncMessage.TYPE_APPLY_MARKDOWN, messages.get(0).getType());
        Assert.assertEquals(2L, messages.get(0).getContentVersion());
        Assert.assertEquals("# Title\n\nnext\n", messages.get(0).getPayload().getString("markdown"));
    }

    /**
     * 验证预览页处于本地未保存脏状态时，宿主不会再主动下发 applyMarkdown 或 revealSourceLine，
     * 以避免源码编辑器的实时同步直接覆盖预览页中的未保存本地编辑。
     */
    @Test
    public void shouldSuspendHostPushWhilePreviewIsDirty() {
        PreviewEditorSyncCoordinator coordinator = new PreviewEditorSyncCoordinator("E:/demo.md");

        coordinator.updateDocument("# Title\n", 1L);
        coordinator.handlePreviewReady();
        coordinator.handlePreviewRendered(1L);
        coordinator.handlePreviewDirtyChanged(true);

        Assert.assertTrue(coordinator.updateDocument("# Changed\n", 2L).isEmpty());
        Assert.assertTrue(coordinator.requestRevealSourceLine(8, 0.5d, "scroll").isEmpty());

        coordinator.handlePreviewDirtyChanged(false);

        List<PreviewSyncMessage> messages = coordinator.updateDocument("# Changed again\n", 3L);
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals(PreviewSyncMessage.TYPE_APPLY_MARKDOWN, messages.get(0).getType());
        Assert.assertEquals(3L, messages.get(0).getContentVersion());
    }
}
