package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 编辑器激活切换判定测试。
 * 该测试用于约束“预览 -> 源码”的自动恢复只能发生在真正从预览编辑器切回源码编辑器的场景，
 * 避免普通源码焦点切换、并列源码窗口切换或其他非目标场景误触发恢复逻辑。
 */
public class EditorActivationTransitionSupportTest {

    /**
     * 验证当旧编辑器是预览、新编辑器是源码时，应允许执行“预览 -> 源码”的恢复定位。
     * 这是当前联动链路中唯一应该触发该恢复逻辑的目标场景。
     */
    @Test
    public void shouldRestoreWhenSwitchingFromPreviewToSourceEditor() {
        Assert.assertTrue(EditorActivationTransitionSupport.shouldRestoreSourceFromPreview(true, true));
    }

    /**
     * 验证当旧编辑器并不是预览编辑器时，不应执行“预览 -> 源码”的恢复定位。
     * 这用于拦截普通源码编辑器之间的切换误触发。
     */
    @Test
    public void shouldNotRestoreWhenOldEditorIsNotPreview() {
        Assert.assertFalse(EditorActivationTransitionSupport.shouldRestoreSourceFromPreview(false, true));
    }

    /**
     * 验证当新编辑器不是源码编辑器时，不应执行恢复定位。
     * 即使旧编辑器是预览，只要切换目标不是源码编辑器，也不应消费预览锚点。
     */
    @Test
    public void shouldNotRestoreWhenNewEditorIsNotSource() {
        Assert.assertFalse(EditorActivationTransitionSupport.shouldRestoreSourceFromPreview(true, false));
    }
}
