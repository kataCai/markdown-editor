package com.shuzijun.markdown.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 标签页右键菜单中的“强制刷新当前页面”动作。
 * 该动作仅对当前激活的 {@link MarkdownPreviewFileEditor} 生效，
 * 通过重建当前预览页的 JCEF 面板来模拟“关闭后重新打开标签页”的恢复路径，
 * 用于降低 Markdown 预览偶发白屏时的恢复成本。
 */
public class ForceRefreshPreviewAction extends AnAction {

    /**
     * 根据当前选中编辑器类型控制动作是否显示。
     * 只有在当前标签页对应的编辑器为 {@link MarkdownPreviewFileEditor} 时，
     * 才允许用户触发该强制刷新动作，避免菜单项出现在无关编辑器上。
     *
     * @param e IntelliJ Action 事件上下文
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = getMarkdownPreviewFileEditor(e) != null;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    /**
     * 对当前激活的 Markdown 预览页执行强制刷新。
     * 这里调用的是编辑器内部的重建逻辑，而不是简单的浏览器 reload，
     * 以提升白屏场景下的恢复成功率并保持与现有恢复路径的行为一致性。
     *
     * @param e IntelliJ Action 事件上下文
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        MarkdownPreviewFileEditor fileEditor = getMarkdownPreviewFileEditor(e);
        if (fileEditor != null) {
            fileEditor.forceRefreshPreview();
        }
    }

    /**
     * 提取当前激活的 Markdown 预览编辑器实例。
     * 这里沿用项目中既有 action 的编辑器获取方式，统一通过 {@link FileEditorManager}
     * 读取当前选中的标签页编辑器，避免引入额外的上下文分发差异。
     *
     * @param e IntelliJ Action 事件上下文
     * @return 当前选中的 {@link MarkdownPreviewFileEditor}；若当前标签页不是该类型则返回 {@code null}
     */
    private @Nullable MarkdownPreviewFileEditor getMarkdownPreviewFileEditor(@NotNull AnActionEvent e) {
        if (e.getProject() == null) {
            return null;
        }
        FileEditor fileEditor = FileEditorManager.getInstance(e.getProject()).getSelectedEditor();
        if (fileEditor instanceof MarkdownPreviewFileEditor) {
            return (MarkdownPreviewFileEditor) fileEditor;
        }
        return null;
    }
}
