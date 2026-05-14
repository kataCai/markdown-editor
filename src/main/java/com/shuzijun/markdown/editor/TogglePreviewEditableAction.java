package com.shuzijun.markdown.editor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.shuzijun.markdown.model.PluginConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 标签页右键菜单中的 Markdown 预览可编辑开关。
 * <p>
 * 该动作只在当前选中的编辑器是 {@link MarkdownPreviewFileEditor} 时展示，
 * 用于控制 Markdown Editor 预览页是否允许用户直接修改内容。状态会持久化为全局默认值，
 * 后续新打开的 Markdown 预览页会沿用该设置；切换时也会立即同步到当前预览实例。
 * 默认未勾选，表示预览页不可编辑。
 */
public class TogglePreviewEditableAction extends ToggleAction {

    /**
     * 读取当前预览可编辑状态，用于在菜单中展示勾选状态。
     *
     * @param e IntelliJ Action 事件上下文
     * @return {@code true} 表示允许预览页编辑；{@code false} 表示禁止预览页编辑
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewEditableKey, false);
    }

    /**
     * 持久化新的预览可编辑状态，并立即同步到当前激活的 Markdown 预览页。
     *
     * @param e IntelliJ Action 事件上下文
     * @param state 用户刚切换出的目标状态，{@code true} 表示允许编辑
     */
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(PluginConstant.editorPreviewEditableKey, state, false);
        MarkdownPreviewFileEditor fileEditor = getMarkdownPreviewFileEditor(e);
        if (fileEditor != null) {
            fileEditor.setPreviewEditable(state);
        }
    }

    /**
     * 控制菜单项只在 Markdown Editor 预览标签页上展示，避免污染普通文本编辑器菜单。
     *
     * @param e IntelliJ Action 事件上下文
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(getMarkdownPreviewFileEditor(e) != null);
    }

    /**
     * 获取当前激活的 Markdown 预览编辑器实例。
     *
     * @param e IntelliJ Action 事件上下文
     * @return 当前激活的 {@link MarkdownPreviewFileEditor}；若当前标签页不是预览编辑器则返回 {@code null}
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
