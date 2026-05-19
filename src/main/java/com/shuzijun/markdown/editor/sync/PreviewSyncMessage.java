package com.shuzijun.markdown.editor.sync;

import com.alibaba.fastjson.JSONObject;

/**
 * 预览联动统一消息对象。
 * 该对象用于收敛 Java 宿主与 JCEF 预览页之间的结构化消息协议，避免继续叠加零散的 JS 片段调用。
 * 一条消息由基础元信息和 payload 两部分组成：
 * 1. 基础元信息用于做跨文件过滤、过期版本过滤和来源判断。
 * 2. payload 用于承载具体同步动作的数据。
 */
public class PreviewSyncMessage {

    public static final String TYPE_PREVIEW_READY = "previewReady";
    public static final String TYPE_PREVIEW_RENDERED = "previewRendered";
    public static final String TYPE_PREVIEW_DIRTY_CHANGED = "previewDirtyChanged";
    public static final String TYPE_PREVIEW_SELECTION_CHANGED = "previewSelectionChanged";
    public static final String TYPE_PREVIEW_VIEWPORT_CHANGED = "previewViewportChanged";
    public static final String TYPE_APPLY_MARKDOWN = "applyMarkdown";
    public static final String TYPE_REVEAL_SOURCE_LINE = "revealSourceLine";

    private final String type;
    private final String filePath;
    private final long contentVersion;
    private final long timestamp;
    private final String source;
    private final JSONObject payload;

    /**
     * 创建统一同步消息。
     *
     * @param type           消息类型
     * @param filePath       当前文档绝对路径，用于避免跨文件消息污染
     * @param contentVersion 当前消息关联的内容版本
     * @param timestamp      消息生成时间戳
     * @param source         消息来源，例如 user、programmatic、activation
     * @param payload        结构化业务负载
     */
    public PreviewSyncMessage(String type,
                              String filePath,
                              long contentVersion,
                              long timestamp,
                              String source,
                              JSONObject payload) {
        this.type = type;
        this.filePath = filePath;
        this.contentVersion = contentVersion;
        this.timestamp = timestamp;
        this.source = source;
        this.payload = payload == null ? new JSONObject() : payload;
    }

    /**
     * 创建 applyMarkdown 指令。
     * 该指令用于宿主把最新 Markdown 文本和内容版本号推送给预览页。
     *
     * @param filePath       当前文档路径
     * @param contentVersion 当前内容版本
     * @param source         来源标记
     * @param markdown       需要应用到预览页的 Markdown 文本
     * @param reason         触发原因，例如 documentChanged、activation、forceRefresh
     * @return 结构化消息对象
     */
    public static PreviewSyncMessage applyMarkdown(String filePath,
                                                   long contentVersion,
                                                   String source,
                                                   String markdown,
                                                   String reason) {
        JSONObject payload = new JSONObject();
        payload.put("markdown", markdown);
        payload.put("reason", reason);
        return new PreviewSyncMessage(
                TYPE_APPLY_MARKDOWN,
                filePath,
                contentVersion,
                System.currentTimeMillis(),
                source,
                payload
        );
    }

    /**
     * 创建 revealSourceLine 指令。
     * 该指令用于宿主驱动预览页滚动到某条源码语义行附近，并尽量保持与源码视口相似的相对位置。
     *
     * @param filePath       当前文档路径
     * @param contentVersion 当前内容版本
     * @param source         来源标记
     * @param line           目标源码行
     * @param topRatio       目标行在可视区内的相对位置比例
     * @param reason         触发原因，例如 caret、scroll、activation
     * @return 结构化消息对象
     */
    public static PreviewSyncMessage revealSourceLine(String filePath,
                                                      long contentVersion,
                                                      String source,
                                                      int line,
                                                      double topRatio,
                                                      String reason) {
        JSONObject payload = new JSONObject();
        payload.put("line", line);
        payload.put("topRatio", topRatio);
        payload.put("reason", reason);
        return new PreviewSyncMessage(
                TYPE_REVEAL_SOURCE_LINE,
                filePath,
                contentVersion,
                System.currentTimeMillis(),
                source,
                payload
        );
    }

    public String getType() {
        return type;
    }

    public String getFilePath() {
        return filePath;
    }

    public long getContentVersion() {
        return contentVersion;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSource() {
        return source;
    }

    public JSONObject getPayload() {
        return payload;
    }
}
