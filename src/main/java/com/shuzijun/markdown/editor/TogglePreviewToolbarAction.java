package com.shuzijun.markdown.editor;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.shuzijun.markdown.model.PluginConstant;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器 TAB 右键菜单中的预览工具栏显隐开关。
 * <p>
 * 该开关只服务于 Markdown 预览编辑器场景：
 * 1. 仅当当前选中的编辑器是 {@link MarkdownPreviewFileEditor} 时展示；
 * 2. 切换结果会持久化为全局默认值，后续打开的 Markdown 预览页沿用该状态；
 * 3. 切换后会立即同步到当前激活的预览页，避免用户还需要重新打开文件才能看到效果。
 */
public class TogglePreviewToolbarAction extends ToggleAction {

    /**
     * 根据当前预览工具栏的全局默认值，返回右键菜单复选状态。
     *
     * @param e IntelliJ Action 事件上下文
     * @return {@code true} 表示应显示预览工具栏，{@code false} 表示应隐藏
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewToolbarVisibleKey, false);
    }

    /**
     * 持久化新的显隐状态，并立即同步给当前激活的 Markdown 预览编辑器实例。
     *
     * @param e     IntelliJ Action 事件上下文
     * @param state 用户刚切换出的目标状态，{@code true} 表示显示，{@code false} 表示隐藏
     */
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        PropertiesComponent.getInstance().setValue(PluginConstant.editorPreviewToolbarVisibleKey, state, false);
        MarkdownPreviewFileEditor fileEditor = getMarkdownPreviewFileEditor(e);
        if (fileEditor != null) {
            fileEditor.setPreviewToolbarVisible(state);
        }
    }

    /**
     * 根据当前选中的编辑器类型，控制右键菜单项是否展示。
     *
     * @param e IntelliJ Action 事件上下文
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = getMarkdownPreviewFileEditor(e) != null;
        e.getPresentation().setEnabledAndVisible(visible);
    }

    /**
     * 提取当前激活的 Markdown 预览编辑器。
     * 这里沿用项目里现有 action 的取值方式，从 {@link FileEditorManager} 读取当前选中的编辑器，
     * 以保持和现有搜索、开发者工具 action 的行为一致。
     *
     * @param e IntelliJ Action 事件上下文
     * @return 当前激活的 {@link MarkdownPreviewFileEditor}；若不存在则返回 {@code null}
     */
    private MarkdownPreviewFileEditor getMarkdownPreviewFileEditor(@NotNull AnActionEvent e) {
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
