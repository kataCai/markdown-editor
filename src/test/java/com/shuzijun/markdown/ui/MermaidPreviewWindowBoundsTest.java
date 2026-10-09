package com.shuzijun.markdown.ui;

import org.junit.Assert;
import org.junit.Test;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;

/**
 * Mermaid 整屏查看窗口的边界计算测试。
 * 这些测试约束窗口矩形来自显示器完整像素范围，而不是 IDE Frame 或预览标签页的宽高。
 */
public class MermaidPreviewWindowBoundsTest {

    /**
     * IDE 窗口落在一块更大的屏幕内时，查看窗口应使用这块屏幕的完整矩形。
     */
    @Test
    public void shouldUseIntersectingScreenBoundsInsteadOfIdeFrameSize() {
        Rectangle ideFrameBounds = new Rectangle(120, 80, 900, 600);
        Rectangle currentScreenBounds = new Rectangle(0, 0, 1920, 1080);
        Rectangle otherScreenBounds = new Rectangle(1920, 0, 1600, 900);
        Rectangle fallbackScreenBounds = new Rectangle(0, 0, 800, 600);

        Assert.assertEquals(
                currentScreenBounds,
                MermaidPreviewWindow.calculateFullScreenBounds(
                        ideFrameBounds,
                        Arrays.asList(otherScreenBounds, currentScreenBounds),
                        fallbackScreenBounds
                )
        );
    }

    /**
     * 拿不到 IDE Frame 时，退回调用方提供的默认屏幕完整矩形。
     */
    @Test
    public void shouldFallbackToDefaultScreenBoundsWhenIdeFrameIsMissing() {
        Rectangle fallbackScreenBounds = new Rectangle(0, 0, 2560, 1440);
        Rectangle unrelatedScreenBounds = new Rectangle(10, 10, 800, 600);

        Assert.assertEquals(
                fallbackScreenBounds,
                MermaidPreviewWindow.calculateFullScreenBounds(
                        null,
                        Collections.singletonList(unrelatedScreenBounds),
                        fallbackScreenBounds
                )
        );
    }
}
