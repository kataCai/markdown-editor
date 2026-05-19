package com.shuzijun.markdown.editor.sync;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Markdown 源码映射解析测试。
 * 该测试聚焦表格场景的源码锚点精度，确保后续预览页能够拿到“整表块 + 表格行级子锚点”的结构化结果，
 * 而不是继续只能对整张表做粗粒度 start/end line 插值。
 */
public class MarkdownSourceMapParserTest {

    /**
     * 验证普通表格会被识别为一个表格块，并为表头、分隔符和每条数据行生成行级锚点。
     * 这对应“预览页位于表格内部时，回写源码应尽量贴近当前表格行”的基础前提。
     */
    @Test
    public void shouldBuildRowAnchorsForMarkdownTable() {
        String markdown = ""
                + "# Title\n"
                + "\n"
                + "| name | value |\n"
                + "| ---- | ----- |\n"
                + "| a    | 1     |\n"
                + "| b    | 2     |\n"
                + "\n"
                + "tail\n";

        List<MarkdownSourceMapParser.BlockMapping> blocks = MarkdownSourceMapParser.parse(markdown);

        Assert.assertEquals(3, blocks.size());
        MarkdownSourceMapParser.BlockMapping tableBlock = blocks.get(1);
        Assert.assertEquals(MarkdownSourceMapParser.BlockType.TABLE, tableBlock.getBlockType());
        Assert.assertEquals(2, tableBlock.getStartLine());
        Assert.assertEquals(5, tableBlock.getEndLine());
        Assert.assertEquals(4, tableBlock.getRowMappings().size());
        Assert.assertEquals(2, tableBlock.getRowMappings().get(0).getStartLine());
        Assert.assertEquals(3, tableBlock.getRowMappings().get(1).getStartLine());
        Assert.assertEquals(4, tableBlock.getRowMappings().get(2).getStartLine());
        Assert.assertEquals(5, tableBlock.getRowMappings().get(3).getStartLine());
    }

    /**
     * 验证非表格内容不会错误地产生表格行级锚点。
     * 这样可以避免后续把普通段落或列表误当成表格结构处理。
     */
    @Test
    public void shouldNotCreateRowAnchorsForNonTableBlocks() {
        String markdown = ""
                + "para one\n"
                + "\n"
                + "- item 1\n"
                + "- item 2\n";

        List<MarkdownSourceMapParser.BlockMapping> blocks = MarkdownSourceMapParser.parse(markdown);

        Assert.assertEquals(2, blocks.size());
        Assert.assertTrue(blocks.get(0).getRowMappings().isEmpty());
        Assert.assertTrue(blocks.get(1).getRowMappings().isEmpty());
    }
}
