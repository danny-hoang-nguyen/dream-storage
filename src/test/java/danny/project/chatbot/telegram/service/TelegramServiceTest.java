package danny.project.chatbot.telegram.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class TelegramServiceTest {

    @Test
    void convertMarkdownToHtml_nullInput_returnsNull() {
        assertNull(TelegramService.convertMarkdownToHtml(null));
    }

    @Test
    void convertMarkdownToHtml_blankInput_returnsBlank() {
        assertEquals("   ", TelegramService.convertMarkdownToHtml("   "));
    }

    @Test
    void convertMarkdownToHtml_boldText() {
        String result = TelegramService.convertMarkdownToHtml("This is **bold** text");
        assertEquals("This is <b>bold</b> text", result);
    }

    @Test
    void convertMarkdownToHtml_italicText() {
        String result = TelegramService.convertMarkdownToHtml("This is _italic_ text");
        assertEquals("This is <i>italic</i> text", result);
    }

    @Test
    void convertMarkdownToHtml_inlineCode() {
        String result = TelegramService.convertMarkdownToHtml("Use `code` here");
        assertEquals("Use <code>code</code> here", result);
    }

    @Test
    void convertMarkdownToHtml_link() {
        String result = TelegramService.convertMarkdownToHtml("Check [this link](https://example.com)");
        assertEquals("Check <a href=\"https://example.com\">this link</a>", result);
    }

    @Test
    void convertMarkdownToHtml_bareLink() {
        String result = TelegramService.convertMarkdownToHtml("See PROJ-123 (https://jira.com/browse/PROJ-123)");
        assertEquals("See <a href=\"https://jira.com/browse/PROJ-123\">PROJ-123</a>", result);
    }

    @Test
    void convertMarkdownToHtml_headerToBold() {
        assertEquals("<b>Header 1</b>", TelegramService.convertMarkdownToHtml("# Header 1"));
        assertEquals("<b>Header 2</b>", TelegramService.convertMarkdownToHtml("## Header 2"));
        assertEquals("<b>Header 3</b>", TelegramService.convertMarkdownToHtml("### Header 3"));
    }

    @Test
    void convertMarkdownToHtml_bulletPoint() {
        String result = TelegramService.convertMarkdownToHtml("- item 1\n- item 2");
        assertTrue(result.contains("• item 1"));
        assertTrue(result.contains("• item 2"));
    }

    @Test
    void convertMarkdownToHtml_horizontalRule_removed() {
        String result = TelegramService.convertMarkdownToHtml("text\n---\nmore");
        assertEquals("text\n\nmore", result);
    }

    @Test
    void convertMarkdownToHtml_codeBlock() {
        String input = "```\nSystem.out.println(\"hello\");\n```";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("<pre>"));
        assertTrue(result.contains("System.out.println"));
        assertTrue(result.contains("</pre>"));
    }

    @Test
    void convertMarkdownToHtml_codeBlockWithLanguage() {
        String input = "```java\nint x = 1;\n```";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("<pre>"));
        assertTrue(result.contains("int x = 1;"));
    }

    @Test
    void convertMarkdownToHtml_unclosedCodeBlock() {
        String input = "```\nSystem.out.println(\"hello\");";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("<pre>"));
        assertTrue(result.contains("System.out.println"));
        assertTrue(result.contains("</pre>"));
    }

    @Test
    void convertMarkdownToHtml_table_convertsToPlainFormat() {
        String input = "| Name | Age |\n|---|---|\n| Alice | 30 |\n| Bob | 25 |";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("<b>Name | Age</b>"));
        assertTrue(result.contains("• Alice | 30"));
        assertTrue(result.contains("• Bob | 25"));
    }

    @Test
    void convertMarkdownToHtml_escapesHtmlCharacters() {
        String result = TelegramService.convertMarkdownToHtml("use <tag> & \"text\"");
        assertFalse(result.contains("<tag>"));
        assertTrue(result.contains("&lt;tag&gt;"));
        assertTrue(result.contains("&amp;"));
    }

    @Test
    void convertMarkdownToHtml_mixedFormatting() {
        String input = "# Title\n\nThis is **bold** and _italic_ and `code`.\n\n- item one\n- item two";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("<b>Title</b>"));
        assertTrue(result.contains("<b>bold</b>"));
        assertTrue(result.contains("<i>italic</i>"));
        assertTrue(result.contains("<code>code</code>"));
        assertTrue(result.contains("• item one"));
    }

    @Test
    void convertMarkdownToHtml_longText_truncated() {
        String longText = "a".repeat(4500);
        String result = TelegramService.convertMarkdownToHtml(longText);
        assertTrue(result.length() <= 4005);
        assertTrue(result.endsWith("..."));
    }

    @Test
    void convertMarkdownToHtml_shortText_notTruncated() {
        String shortText = "Hello world";
        String result = TelegramService.convertMarkdownToHtml(shortText);
        assertEquals(shortText, result);
    }

    @Test
    void convertMarkdownToHtml_plainText_unchanged() {
        String text = "This is a simple plain text message without any markdown.";
        assertEquals(text, TelegramService.convertMarkdownToHtml(text));
    }

    @Test
    void convertMarkdownToHtml_emptyString_returnsEmpty() {
        assertEquals("", TelegramService.convertMarkdownToHtml(""));
    }

    @ParameterizedTest
    @CsvSource({
            "'`inline code` with **bold**', '<code>inline code</code> with <b>bold</b>'",
            "'_italic_ and `code` together', '<i>italic</i> and <code>code</code> together'",
            "'**bold** _italic_', '<b>bold</b> <i>italic</i>'"
    })
    void convertMarkdownToHtml_inlineCombinations(String input, String expected) {
        assertEquals(expected, TelegramService.convertMarkdownToHtml(input));
    }

    @Test
    void convertMarkdownToHtml_multipleParagraphs() {
        String input = "First paragraph.\n\nSecond paragraph.";
        String result = TelegramService.convertMarkdownToHtml(input);
        assertTrue(result.contains("First paragraph."));
        assertTrue(result.contains("Second paragraph."));
    }

    @Test
    void convertMarkdownToHtml_specialCharacters_escaped() {
        String result = TelegramService.convertMarkdownToHtml("a < b && c > d");
        assertTrue(result.contains("a &lt; b &amp;&amp; c &gt; d"));
    }

    @Test
    void convertMarkdownToHtml_linkInsideBold() {
        String result = TelegramService.convertMarkdownToHtml("**Click [here](https://example.com)**");
        assertTrue(result.contains("<b>"));
        assertTrue(result.contains("<a href"));
    }
}
