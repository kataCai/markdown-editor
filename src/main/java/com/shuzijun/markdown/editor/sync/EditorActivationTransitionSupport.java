package com.shuzijun.markdown.editor.sync;

/**
 * 编辑器激活切换判定辅助类。
 * 该类用于约束“预览 -> 源码”的激活恢复只能发生在真正从预览编辑器切回源码编辑器的场景，
 * 避免普通源码编辑器之间的切换、其他文件编辑器切换或非目标焦点切换误消费预览锚点。
 */
public final class EditorActivationTransitionSupport {

    private EditorActivationTransitionSupport() {
    }

    /**
     * 判断当前激活切换是否应执行“预览 -> 源码”的恢复定位。
     *
     * @param oldEditorIsPreview {@code true} 表示旧编辑器就是当前 Markdown 预览编辑器
     * @param newEditorIsSource  {@code true} 表示新编辑器是当前 Markdown 文件对应的源码编辑器
     * @return 仅当旧编辑器为预览且新编辑器为源码时返回 {@code true}
     */
    public static boolean shouldRestoreSourceFromPreview(boolean oldEditorIsPreview, boolean newEditorIsSource) {
        return oldEditorIsPreview && newEditorIsSource;
    }
}
