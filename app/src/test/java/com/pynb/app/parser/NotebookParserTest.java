package com.pynb.app.parser;

import com.pynb.app.model.CellOutput;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.model.OutlineItem;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NotebookParserTest {

    private static final String SAMPLE_JSON = "{\n" +
            "  \"metadata\": {\n" +
            "    \"kernelspec\": {\n" +
            "      \"name\": \"python3\",\n" +
            "      \"display_name\": \"Python 3 (ipykernel)\"\n" +
            "    },\n" +
            "    \"language_info\": {\n" +
            "      \"name\": \"python\",\n" +
            "      \"version\": \"3.11.0\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"nbformat\": 4,\n" +
            "  \"nbformat_minor\": 5,\n" +
            "  \"cells\": [\n" +
            "    {\n" +
            "      \"cell_type\": \"markdown\",\n" +
            "      \"metadata\": {},\n" +
            "      \"source\": [\"# Main Title\\n\", \"Some description\\n\", \"## Sub Heading\"]\n" +
            "    },\n" +
            "    {\n" +
            "      \"cell_type\": \"code\",\n" +
            "      \"execution_count\": 1,\n" +
            "      \"metadata\": {},\n" +
            "      \"source\": \"print('Hello Jupyter!')\",\n" +
            "      \"outputs\": [\n" +
            "        {\n" +
            "          \"output_type\": \"stream\",\n" +
            "          \"name\": \"stdout\",\n" +
            "          \"text\": [\"Hello Jupyter!\\n\"]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    {\n" +
            "      \"cell_type\": \"code\",\n" +
            "      \"execution_count\": 2,\n" +
            "      \"metadata\": {},\n" +
            "      \"source\": \"raise ValueError('intentional error')\",\n" +
            "      \"outputs\": [\n" +
            "        {\n" +
            "          \"output_type\": \"error\",\n" +
            "          \"ename\": \"ValueError\",\n" +
            "          \"evalue\": \"intentional error\",\n" +
            "          \"traceback\": [\"\\u001b[0;31mValueError\\u001b[0m: intentional error\"]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    @Test
    public void testParseNotebook() throws Exception {
        Notebook notebook = NotebookParser.parse(SAMPLE_JSON);
        assertNotNull(notebook);
        assertEquals(4, notebook.getNbformat());
        assertEquals(5, notebook.getNbformatMinor());
        assertEquals("Python 3 (ipykernel)", notebook.getMetadata().getKernelDisplayName());
        assertEquals("python", notebook.getMetadata().getLanguageName());
        assertEquals(3, notebook.getTotalCells());
        assertEquals(2, notebook.getCodeCellCount());
        assertEquals(1, notebook.getMarkdownCellCount());

        // Cell 0: Markdown
        NotebookCell cell0 = notebook.getCells().get(0);
        assertTrue(cell0.isMarkdown());
        assertTrue(cell0.getSource().contains("# Main Title"));

        // Cell 1: Code with stream output
        NotebookCell cell1 = notebook.getCells().get(1);
        assertTrue(cell1.isCode());
        assertEquals(Integer.valueOf(1), cell1.getExecutionCount());
        assertEquals("print('Hello Jupyter!')", cell1.getSource());
        assertEquals(1, cell1.getOutputs().size());
        CellOutput out0 = cell1.getOutputs().get(0);
        assertTrue(out0.isStream());
        assertEquals("stdout", out0.getName());
        assertEquals("Hello Jupyter!\n", out0.getPlainText());

        // Cell 2: Code with error output
        NotebookCell cell2 = notebook.getCells().get(2);
        assertTrue(cell2.isCode());
        CellOutput errOut = cell2.getOutputs().get(0);
        assertTrue(errOut.isError());
        assertEquals("ValueError", errOut.getEname());
        assertEquals("intentional error", errOut.getEvalue());
        assertFalse(errOut.getTraceback().isEmpty());
    }

    @Test
    public void testOutlineExtraction() throws Exception {
        Notebook notebook = NotebookParser.parse(SAMPLE_JSON);
        List<OutlineItem> outline = notebook.getOutline();
        assertNotNull(outline);
        assertEquals(2, outline.size());

        assertEquals(1, outline.get(0).getLevel());
        assertEquals("Main Title", outline.get(0).getTitle());
        assertEquals(0, outline.get(0).getCellIndex());

        assertEquals(2, outline.get(1).getLevel());
        assertEquals("Sub Heading", outline.get(1).getTitle());
        assertEquals(0, outline.get(1).getCellIndex());
    }

    @Test
    public void testAnsiStripping() {
        String ansiText = "\u001B[0;31mError\u001B[0m in cell";
        String clean = AnsiParser.stripAnsi(ansiText);
        assertEquals("Error in cell", clean);
    }
}
