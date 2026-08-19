package com.shuzijun.markdown.editor;

import org.junit.Assert;
import org.junit.Test;

/**
 * TogglePreviewToolbarAction 的文案映射单测。
 * <p>
 * 这组测试只验证“当前工具栏状态 -> 菜单显示文案”的纯映射逻辑，不依赖 IntelliJ UI 或 editor harness，
 * 用于保证右键菜单始终提示用户执行相反方向的操作。
 */
public class TogglePreviewToolbarActionTest {

    /**
     * 验证工具栏隐藏时，菜单应提示用户执行显示操作。
     *
     * @return 无返回值。
     */
    @Test
    public void shouldResolveShowPreviewToolbarTextWhenToolbarIsHidden() {
        TogglePreviewToolbarAction action = new TogglePreviewToolbarAction();

        Assert.assertEquals("Show Preview Toolbar", action.resolvePreviewToolbarText(false));
    }

    /**
     * 验证工具栏显示时，菜单应提示用户执行隐藏操作。
     *
     * @return 无返回值。
     */
    @Test
    public void shouldResolveHidePreviewToolbarTextWhenToolbarIsVisible() {
        TogglePreviewToolbarAction action = new TogglePreviewToolbarAction();

        Assert.assertEquals("Hide Preview Toolbar", action.resolvePreviewToolbarText(true));
    }
}
