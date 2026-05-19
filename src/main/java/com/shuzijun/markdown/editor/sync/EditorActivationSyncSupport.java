package com.shuzijun.markdown.editor.sync;

/**
 * 源码编辑器激活恢复辅助类。
 * 该类专门负责判断“从预览切回源码编辑器时，这次恢复定位是否应当视为 no-op”。
 * 适用场景主要包括：
 * 1. 目标行本来就在当前源码可视区内，此时再次 open/reveal 只会制造抖动；
 * 2. 同一目标行在极短时间内被重复恢复，多数属于激活切换回声或布局抖动导致的重复消费。
 * 该类只做纯判断，不直接依赖 IntelliJ 平台对象，便于单元测试覆盖边界。
 */
public final class EditorActivationSyncSupport {

    /**
     * 同一目标行重复恢复的短时间窗口。
     * 只要仍处于该时间窗内，并且目标行与上次恢复一致，就将其视为重复恢复并直接跳过。
     */
    private static final long DUPLICATED_RESTORE_WINDOW_MS = 160L;

    private EditorActivationSyncSupport() {
    }

    /**
     * 判断当前源码恢复是否应被跳过。
     * 关键规则如下：
     * 1. 若目标行已处于当前可视区内，则说明无需再次滚动或重新打开编辑器；
     * 2. 若与最近一次恢复的目标行相同，且发生在极短时间窗口内，则说明大概率是切换回声，应视为 no-op。
     *
     * @param visibleTopLine         当前源码编辑器可视区顶部逻辑行号
     * @param visibleBottomLine      当前源码编辑器可视区底部逻辑行号
     * @param targetLine             本次准备恢复到的目标逻辑行号
     * @param lastRestoredTargetLine 最近一次已执行恢复的目标逻辑行号；若无则传入负数
     * @param lastRestoreAtMillis    最近一次已执行恢复的时间戳；若无则传入 0
     * @param nowMillis              当前判断时刻的时间戳
     * @return 若返回 {@code true}，表示本次恢复属于无效或重复恢复，应直接跳过
     */
    public static boolean shouldSkipSourceRestore(int visibleTopLine,
                                                  int visibleBottomLine,
                                                  int targetLine,
                                                  int lastRestoredTargetLine,
                                                  long lastRestoreAtMillis,
                                                  long nowMillis) {
        int normalizedTopLine = Math.min(visibleTopLine, visibleBottomLine);
        int normalizedBottomLine = Math.max(visibleTopLine, visibleBottomLine);
        if (targetLine >= normalizedTopLine && targetLine <= normalizedBottomLine) {
            return true;
        }
        return targetLine == lastRestoredTargetLine
                && lastRestoreAtMillis > 0L
                && nowMillis - lastRestoreAtMillis <= DUPLICATED_RESTORE_WINDOW_MS;
    }
}
