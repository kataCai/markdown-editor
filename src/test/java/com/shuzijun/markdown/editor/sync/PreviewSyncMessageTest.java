package com.shuzijun.markdown.editor.sync;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * 预览同步消息结构测试。
 * 这些测试用于约束 Java 与 JCEF 页面之间的统一消息协议，避免后续继续堆散乱的单点脚本调用。
 */
public class PreviewSyncMessageTest {

    /**
     * 验证 revealSourceLine 指令会带齐联动所需的基础元信息和结构化 payload。
     * 这里特别关注行号、相对视口比例和原因字段是否稳定进入消息体。
     */
    @Test
    public void shouldCreateRevealSourceLineMessageWithStructuredPayload() {
        PreviewSyncMessage message = PreviewSyncMessage.revealSourceLine(
                "/tmp/demo.md",
                7L,
                "user",
                23,
                0.4d,
                "caret"
        );

        Assert.assertEquals(PreviewSyncMessage.TYPE_REVEAL_SOURCE_LINE, message.getType());
        Assert.assertEquals("/tmp/demo.md", message.getFilePath());
        Assert.assertEquals(7L, message.getContentVersion());
        Assert.assertEquals("user", message.getSource());
        Assert.assertEquals(23, message.getPayload().getIntValue("line"));
        Assert.assertEquals(0.4d, message.getPayload().getDoubleValue("topRatio"), 0.0001d);
        Assert.assertEquals("caret", message.getPayload().getString("reason"));
    }

    /**
     * 验证 applyMarkdown 指令在序列化后仍能保留多行 Markdown 文本。
     * 这是内容版本同步的基础，如果换行或特殊字符丢失，预览与源码将天然失配。
     */
    @Test
    public void shouldPreserveMarkdownPayloadWhenSerialized() {
        String markdown = "# Title\n\n- item 1\n- item 2\n";
        PreviewSyncMessage message = PreviewSyncMessage.applyMarkdown(
                "E:/source_code/demo.md",
                12L,
                "programmatic",
                markdown,
                "documentChanged",
                Collections.singletonList(
                        new MarkdownSourceMapParser.BlockMapping(
                                MarkdownSourceMapParser.BlockType.GENERIC,
                                0,
                                3,
                                Collections.emptyList()
                        )
                )
        );

        String json = JSON.toJSONString(message);
        JSONObject parsed = JSON.parseObject(json);

        Assert.assertEquals(PreviewSyncMessage.TYPE_APPLY_MARKDOWN, parsed.getString("type"));
        Assert.assertEquals(markdown, parsed.getJSONObject("payload").getString("markdown"));
        Assert.assertEquals("documentChanged", parsed.getJSONObject("payload").getString("reason"));
        JSONArray sourceMap = parsed.getJSONObject("payload").getJSONArray("sourceMap");
        Assert.assertEquals(1, sourceMap.size());
        Assert.assertEquals("GENERIC", sourceMap.getJSONObject(0).getString("blockType"));
    }
}
