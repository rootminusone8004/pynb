package com.pynb.app.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MermaidSplitterTest {

    @Test
    public void plainMarkdownIsOneSegment() {
        List<MermaidSplitter.Segment> s = MermaidSplitter.split("# Title\n\nSome text");
        assertEquals(1, s.size());
        assertFalse(s.get(0).mermaid);
        assertFalse(MermaidSplitter.containsMermaid("# Title\n\nSome text"));
    }

    @Test
    public void diagramBetweenMarkdown() {
        String src = "Intro\n\n```mermaid\ngraph TD\n  A-->B\n```\n\nOutro";
        List<MermaidSplitter.Segment> s = MermaidSplitter.split(src);
        assertEquals(3, s.size());
        assertEquals("Intro", s.get(0).text);
        assertTrue(s.get(1).mermaid);
        assertEquals("graph TD\n  A-->B", s.get(1).text);
        assertEquals("Outro", s.get(2).text);
        assertTrue(MermaidSplitter.containsMermaid(src));
    }

    @Test
    public void onlyDiagramAndTildeFenceAndCrlf() {
        List<MermaidSplitter.Segment> s = MermaidSplitter.split("~~~ Mermaid\r\nsequenceDiagram\r\nA->>B: hi\r\n~~~\r\n");
        assertEquals(1, s.size());
        assertTrue(s.get(0).mermaid);
        assertEquals("sequenceDiagram\nA->>B: hi", s.get(0).text);
    }

    @Test
    public void otherCodeBlocksAreLeftAlone() {
        String src = "````markdown\n```mermaid\ngraph TD\n```\n````\n\n```python\nx = 1\n```";
        List<MermaidSplitter.Segment> s = MermaidSplitter.split(src);
        assertEquals(1, s.size());
        assertFalse(s.get(0).mermaid);
        assertEquals(src, s.get(0).text);
    }

    @Test
    public void unclosedFenceRunsToEnd() {
        List<MermaidSplitter.Segment> s = MermaidSplitter.split("Text\n```mermaid\npie\n\"a\": 1");
        assertEquals(2, s.size());
        assertTrue(s.get(1).mermaid);
        assertEquals("pie\n\"a\": 1", s.get(1).text);
    }

    @Test
    public void longerClosingFenceRequiredMatchesOpener() {
        // A ``` line inside a ```` block does not close it.
        List<MermaidSplitter.Segment> s = MermaidSplitter.split("````mermaid\ngraph LR\n```\nA-->B\n````");
        assertEquals(1, s.size());
        assertEquals("graph LR\n```\nA-->B", s.get(0).text);
    }
}
