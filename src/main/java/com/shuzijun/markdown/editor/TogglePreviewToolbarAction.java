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
 * Markdown 预览标签页右键菜单中的“预览工具栏”切换动作。
 * <p>
 * 这个动作只负责两件事：
 * 1. 持久化预览工具栏的全局可见性；
 * 2. 在菜单中按当前状态展示下一步操作文案，避免用户看到固定的 Show 提示。
 * <p>
 * 仅当当前选中的编辑器是 {@link MarkdownPreviewFileEditor} 时才显示。
 */
public class TogglePreviewToolbarAction extends ToggleAction {

    /**
     * 工具栏隐藏时，对应菜单需要提示用户“显示”。
     */
    private static final String SHOW_PREVIEW_TOOLBAR_TEXT = "Show Preview Toolbar";

    /**
     * 工具栏显示时，对应菜单需要提示用户“隐藏”。
     */
    private static final String HIDE_PREVIEW_TOOLBAR_TEXT = "Hide Preview Toolbar";

    /**
     * 根据全局持久化状态返回 ToggleAction 的勾选状态。
     *
     * @param e IntelliJ Action 事件上下文。
     * @return {@code true} 表示当前预览工具栏处于显示状态；{@code false} 表示处于隐藏状态。
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return PropertiesComponent.getInstance().getBoolean(PluginConstant.editorPreviewToolbarVisibleKey, false);
    }

    /**
     * 持久化新的工具栏显示状态，并同步到当前激活的 Markdown 预览编辑器实例。
     *
     * @param e IntelliJ Action 事件上下文。
     * @param state 用户切换后的目标状态，{@code true} 表示显示工具栏，{@code false} 表示隐藏工具栏。
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
     * 仅在 Markdown 预览标签页右键菜单可见时刷新展示状态和文案。
     * <p>
     * 这里先沿用现有的可见性判断，再根据当前持久化状态动态改写菜单文案，
     * 让菜单表达“下一步会发生什么”，而不是始终显示固定入口。
     *
     * @param e IntelliJ Action 事件上下文。
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        boolean visible = getMarkdownPreviewFileEditor(e) != null;
        e.getPresentation().setEnabledAndVisible(visible);
        if (visible) {
            e.getPresentation().setText(resolvePreviewToolbarText(isSelected(e)));
        }
    }

    /**
     * 根据当前工具栏可见性解析右键菜单文案。
     * <p>
     * 这里返回的是“下一步操作”而不是“当前状态”：
     * 当工具栏当前隐藏时，应提示 Show；当工具栏当前显示时，应提示 Hide。
     *
     * @param visible 当前预览工具栏是否可见。
     * @return 对应的菜单文案。
     */
    String resolvePreviewToolbarText(boolean visible) {
        return visible ? HIDE_PREVIEW_TOOLBAR_TEXT : SHOW_PREVIEW_TOOLBAR_TEXT;
    }

    /**
     * 提取当前激活的 Markdown 预览编辑器实例。
     * <p>
     * 这里沿用项目里现有的编辑器获取方式，统一通过 {@link FileEditorManager}
     * 读取当前选中的标签页，避免引入额外状态或绕开现有同步链路。
     *
     * @param e IntelliJ Action 事件上下文。
     * @return 当前激活的 {@link MarkdownPreviewFileEditor}；如果当前标签页不是预览编辑器则返回 {@code null}。
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
