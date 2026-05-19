package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

/**
 * 源码激活恢复保护测试。
 * 该测试用于固定“切回源码编辑器时什么情况下不应该再主动跳转”的规则，
 * 避免频繁切换编辑器时，即便目标本来已经可见，仍然不断触发重复定位。
 */
public class EditorActivationSyncSupportTest {

    /**
     * 验证当目标行已经处于当前源码可视区内时，应直接视为 no-op。
     * 这是避免短文档或无滚动场景下频繁切换后不断重复下跳的第一道保护。
     */
    @Test
    public void shouldTreatVisibleTargetLineAsNoOp() {
        Assert.assertTrue(EditorActivationSyncSupport.shouldSkipSourceRestore(10, 20, 15, -1, 0L, 1000L));
    }

    /**
     * 验证同一目标行在短时间内重复恢复时应直接跳过。
     * 这对应“切到预览再立刻切回源码”这类高频切换场景，避免重复消费同一锚点。
     */
    @Test
    public void shouldTreatRapidDuplicateRestoreAsNoOp() {
        Assert.assertTrue(EditorActivationSyncSupport.shouldSkipSourceRestore(30, 40, 50, 50, 1000L, 1080L));
    }

    /**
     * 验证当目标行不在可视区，且不是刚刚恢复过的同一目标时，仍然应执行恢复。
     * 否则正常的“从预览回到源码”定位会被错误抑制。
     */
    @Test
    public void shouldAllowRestoreForNonVisibleDifferentTarget() {
        Assert.assertFalse(EditorActivationSyncSupport.shouldSkipSourceRestore(10, 20, 35, 18, 1000L, 1300L));
    }
}
