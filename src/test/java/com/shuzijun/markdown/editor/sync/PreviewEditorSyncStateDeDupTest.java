package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 宿主侧编辑器事件去重测试。
 * 该测试固定“光标变化 + 可视区变化”短时间连续到达时的去重策略，
 * 避免同一语义位置被重复下发两次 reveal 指令，从而在预览侧形成不必要的抖动。
 */
public class PreviewEditorSyncStateDeDupTest {

    /**
     * 验证同一语义锚点在短时间窗口内会被合并。
     * 这里模拟 caret 事件刚发过 reveal，紧接着 visibleArea 再上报同一位置时，应判定为重复。
     */
    @Test
    public void shouldCoalesceDuplicateEditorRevealInShortWindow() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        Assert.assertFalse(state.shouldSkipDuplicatedEditorReveal(120, 0.50d, 1000L));

        state.recordEditorRevealDispatch(120, 0.50d, 1000L);

        Assert.assertTrue(state.shouldSkipDuplicatedEditorReveal(120, 0.50d, 1040L));
        Assert.assertTrue(state.shouldSkipDuplicatedEditorReveal(120, 0.515d, 1040L));
    }

    /**
     * 验证只要行号或相对位置出现明显变化，就不应错误合并。
     * 否则用户真实滚动到新位置时，宿主可能错误抑制掉有效同步事件。
     */
    @Test
    public void shouldKeepMeaningfullyDifferentRevealRequests() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.recordEditorRevealDispatch(120, 0.50d, 1000L);

        Assert.assertFalse(state.shouldSkipDuplicatedEditorReveal(121, 0.50d, 1040L));
        Assert.assertFalse(state.shouldSkipDuplicatedEditorReveal(120, 0.70d, 1040L));
        Assert.assertFalse(state.shouldSkipDuplicatedEditorReveal(120, 0.50d, 1200L));
    }
}
