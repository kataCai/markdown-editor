package com.shuzijun.markdown.ui;

import org.junit.Assert;
import org.junit.Test;

import java.awt.Rectangle;

/**
 * 图片查看器窗口边界计算测试。
 * 这些测试用于约束图片查看器的窗口定位策略必须围绕 IDE Frame 边界计算，
 * 而不是仅仅给内容面板一个 preferredSize 后继续交给当前编辑区自行裁剪。
 */
public class ImagePreviewDialogWrapperBoundsTest {

    /**
     * 验证当 IDE Frame 边界可用时，图片查看器会直接复用这组边界。
     * 这个断言覆盖本次最核心的展示需求：独立窗口必须与整个 Android Studio 主窗口对齐，
     * 不能再退回到“只在 Markdown 编辑区大小内放大”的旧体验。
     */
    @Test
    public void shouldReuseIdeFrameBoundsAsDialogBounds() {
        Rectangle ideFrameBounds = new Rectangle(120, 80, 1680, 960);
        Rectangle fallbackBounds = new Rectangle(0, 0, 1440, 900);

        Assert.assertEquals(
                ideFrameBounds,
                ImagePreviewDialogWrapper.calculateDialogBounds(ideFrameBounds, fallbackBounds)
        );
    }

    /**
     * 验证当 IDE Frame 暂时不可用时，会回退到调用方提供的屏幕可用区边界。
     * 这个场景覆盖的是容错分支，避免无项目窗口或启动早期阶段出现空边界后，
     * 又悄悄退回到基于编辑区内容首选尺寸的旧策略。
     */
    @Test
    public void shouldFallbackToAvailableScreenBoundsWhenIdeFrameBoundsAreMissing() {
        Rectangle fallbackBounds = new Rectangle(16, 24, 1440, 900);

        Assert.assertEquals(
                fallbackBounds,
                ImagePreviewDialogWrapper.calculateDialogBounds(null, fallbackBounds)
        );
    }
}
