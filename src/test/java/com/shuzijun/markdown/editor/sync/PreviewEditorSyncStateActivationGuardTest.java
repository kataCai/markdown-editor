package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 激活切换保护测试。
 * 该测试用于固定“无滚动场景伪锚点不应驱动源码跳转”的规则，防止源码/预览频繁切换时内容一轮轮往下漂移。
 */
public class PreviewEditorSyncStateActivationGuardTest {

    /**
     * 验证当最近一次预览锚点不是用户真实滚动产生，而是无滚动场景下的伪锚点时，
     * 切回源码编辑器不应消费它作为自动定位目标。
     */
    @Test
    public void shouldIgnoreSyntheticPreviewAnchorWhenResolvingActivationTarget() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(12, "block-12", PreviewEditorSyncState.PreviewAnchorKind.NO_SCROLL_SYNTHETIC);

        Assert.assertEquals(-1, state.resolveSourceActivationTargetLine());
    }

    /**
     * 验证有效选区仍然具有最高优先级。
     * 即便普通预览锚点无效，只要存在用户选区，切回源码编辑器仍应按选区起始行恢复。
     */
    @Test
    public void shouldPreferPreviewSelectionOverSyntheticAnchor() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(18, "block-18", PreviewEditorSyncState.PreviewAnchorKind.NO_SCROLL_SYNTHETIC);
        state.updatePreviewSelection(7, 9, "cell");

        Assert.assertEquals(7, state.resolveSourceActivationTargetLine());
    }

    /**
     * 验证用户真实滚动产生的预览锚点仍然会被消费。
     * 这保证加了保护之后不会把正常的预览 -> 源码激活恢复一起误伤。
     */
    @Test
    public void shouldUseUserScrollPreviewAnchorWhenItIsValid() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(21, "block-21", PreviewEditorSyncState.PreviewAnchorKind.USER_SCROLL);

        Assert.assertEquals(21, state.resolveSourceActivationTargetLine());
    }

    /**
     * 验证当预览选区被显式清除后，激活恢复应回退到最近一次有效的用户视口锚点。
     * 这用于避免陈旧选区长期滞留在状态对象中，导致后续切换始终优先跳向旧选区。
     */
    @Test
    public void shouldFallBackToViewportAnchorAfterPreviewSelectionIsCleared() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(21, "block-21", PreviewEditorSyncState.PreviewAnchorKind.USER_SCROLL);
        state.updatePreviewSelection(7, 9, "cell");
        state.clearPreviewSelection();

        Assert.assertEquals(21, state.resolveSourceActivationTargetLine());
    }

    /**
     * 验证过期的预览选区不应继续参与激活恢复。
     * 这用于防止用户很久之前在预览里留下的旧选区跨多次切换后仍被宿主优先消费。
     */
    @Test
    public void shouldIgnoreExpiredPreviewSelectionWhenResolvingActivationTarget() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewSelection(7, 9, "cell", 1000L);

        Assert.assertEquals(-1, state.resolveSourceActivationTargetLine(5000L));
    }

    /**
     * 验证过期的预览视口锚点不应继续参与激活恢复。
     * 这用于阻止程序性滚动或很久之前的用户滚动在后续切换时被错误地当成最新锚点消费。
     */
    @Test
    public void shouldIgnoreExpiredPreviewAnchorWhenResolvingActivationTarget() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(21, "block-21", PreviewEditorSyncState.PreviewAnchorKind.USER_SCROLL, 1000L);

        Assert.assertEquals(-1, state.resolveSourceActivationTargetLine(5000L));
    }

    /**
     * 验证近期有效的预览选区和视口锚点仍可正常参与激活恢复。
     * 这用于确保补充过期保护后不会误伤正常的“刚从预览切回源码”定位链路。
     */
    @Test
    public void shouldKeepRecentPreviewInputEligibleForActivationTarget() {
        PreviewEditorSyncState state = new PreviewEditorSyncState();

        state.updatePreviewAnchor(21, "block-21", PreviewEditorSyncState.PreviewAnchorKind.USER_SCROLL, 1000L);
        state.updatePreviewSelection(7, 9, "cell", 1200L);

        Assert.assertEquals(7, state.resolveSourceActivationTargetLine(2500L));
    }
}
