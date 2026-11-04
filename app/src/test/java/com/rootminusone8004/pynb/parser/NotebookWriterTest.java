package com.rootminusone8004.pynb.parser;

import com.rootminusone8004.pynb.model.CellOutput;
import com.rootminusone8004.pynb.model.Notebook;
import com.rootminusone8004.pynb.model.NotebookCell;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NotebookWriterTest {

    /** Formatted exactly like Jupyter writes it (indent=1, sorted keys, split lines). */
    private static final String JUPYTER_NOTEBOOK = "{\n"
            + " \"cells\": [\n"
            + "  {\n"
            + "   \"cell_type\": \"markdown\",\n"
            + "   \"metadata\": {},\n"
            + "   \"source\": [\n"
            + "    \"# Title — ünïcode\\n\",\n"
            + "    \"$x^2$\"\n"
            + "   ]\n"
            + "  },\n"
            + "  {\n"
            + "   \"cell_type\": \"code\",\n"
            + "   \"execution_count\": null,\n"
            + "   \"id\": \"abc\",\n"
            + "   \"metadata\": {\n"
            + "    \"tags\": [\n"
            + "     \"keep\"\n"
            + "    ]\n"
            + "   },\n"
            + "   \"outputs\": [],\n"
            + "   \"source\": [\n"
            + "    \"print('hi')\\n\",\n"
            + "    \"1 + 1\"\n"
            + "   ]\n"
            + "  }\n"
            + " ],\n"
            + " \"metadata\": {\n"
            + "  \"kernelspec\": {\n"
            + "   \"display_name\": \"Python 3\",\n"
            + "   \"language\": \"python\",\n"
            + "   \"name\": \"python3\"\n"
            + "  }\n"
            + " },\n"
            + " \"nbformat\": 4,\n"
            + " \"nbformat_minor\": 5\n"
            + "}\n";

    @Test
    public void unchangedNotebookRoundTripsByteForByte() throws Exception {
        Notebook nb = NotebookParser.parse(JUPYTER_NOTEBOOK);
        assertEquals(JUPYTER_NOTEBOOK, NotebookWriter.write(nb));
    }

    @Test
    public void executedCellGetsOutputsAndKeepsOtherFields() throws Exception {
        Notebook nb = NotebookParser.parse(JUPYTER_NOTEBOOK);
        NotebookCell code = nb.getCells().get(1);

        List<CellOutput> outputs = new ArrayList<>();
        outputs.add(NotebookParser.parseOutput(new JSONObject(
                "{\"output_type\":\"stream\",\"name\":\"stdout\",\"text\":\"hi\\nthere\\n\"}")));
        outputs.add(NotebookParser.parseOutput(new JSONObject(
                "{\"output_type\":\"execute_result\",\"execution_count\":3,\"metadata\":{},"
                        + "\"data\":{\"text/plain\":\"2\",\"image/png\":\"iVBOR\\nAAA\"}}")));
        code.setOutputs(outputs);
        code.setExecutionCount(3);
        code.setModified(true);

        String written = NotebookWriter.write(nb);
        JSONObject cell = new JSONObject(written).getJSONArray("cells").getJSONObject(1);

        assertEquals(3, cell.getInt("execution_count"));
        assertEquals("abc", cell.getString("id"));
        assertEquals("keep", cell.getJSONObject("metadata").getJSONArray("tags").getString(0));

        JSONArray outs = cell.getJSONArray("outputs");
        JSONArray streamText = outs.getJSONObject(0).getJSONArray("text");
        assertEquals("hi\n", streamText.getString(0));
        assertEquals("there\n", streamText.getString(1));

        JSONObject data = outs.getJSONObject(1).getJSONObject("data");
        assertEquals("2", data.getJSONArray("text/plain").getString(0));
        // binary images stay a single string, like nbformat
        assertEquals("iVBOR\nAAA", data.getString("image/png"));

        // untouched markdown cell is unchanged
        assertTrue(written.contains("    \"# Title — ünïcode\\n\",\n"));
        assertTrue(written.endsWith("}\n"));
    }

    @Test
    public void editedSourceIsWrittenAndOutputsKept() throws Exception {
        Notebook nb = NotebookParser.parse(JUPYTER_NOTEBOOK);
        NotebookCell md = nb.getCells().get(0);
        md.setSource("# New title\nline two");
        md.setSourceModified(true);

        String written = NotebookWriter.write(nb);
        JSONObject root = new JSONObject(written);
        JSONArray src = root.getJSONArray("cells").getJSONObject(0).getJSONArray("source");
        assertEquals("# New title\n", src.getString(0));
        assertEquals("line two", src.getString(1));
        // markdown cells never gain code-only fields
        assertFalse(root.getJSONArray("cells").getJSONObject(0).has("outputs"));
        // the untouched code cell is written exactly as before
        assertTrue(written.contains("    \"print('hi')\\n\",\n    \"1 + 1\"\n"));
    }

    @Test
    public void editedCodeCellKeepsItsOutputs() throws Exception {
        String withOutput = JUPYTER_NOTEBOOK.replace("\"outputs\": [],",
                "\"outputs\": [\n    {\n     \"name\": \"stdout\",\n     \"output_type\": \"stream\",\n"
                        + "     \"text\": [\n      \"hi\\n\"\n     ]\n    }\n   ],");
        Notebook nb = NotebookParser.parse(withOutput);
        NotebookCell code = nb.getCells().get(1);
        code.setSource("print('bye')");
        code.setSourceModified(true);

        JSONObject cell = new JSONObject(NotebookWriter.write(nb)).getJSONArray("cells").getJSONObject(1);
        assertEquals("print('bye')", cell.getJSONArray("source").getString(0));
        assertEquals("hi\n", cell.getJSONArray("outputs").getJSONObject(0).getJSONArray("text").getString(0));
        assertTrue(cell.isNull("execution_count"));
    }

    @Test
    public void cellJsonReflectsSavedState() throws Exception {
        Notebook nb = NotebookParser.parse(JUPYTER_NOTEBOOK);
        NotebookCell code = nb.getCells().get(1);
        code.setExecutionCount(1);
        code.setModified(true);
        JSONObject json = NotebookWriter.cellJson(code);
        assertEquals(1, json.getInt("execution_count"));
        code.setRawJson(json);
        code.setModified(false);
        assertEquals(1, NotebookWriter.cellJson(code).getInt("execution_count"));
    }

    @Test(expected = org.json.JSONException.class)
    public void nbformat3IsRejected() throws Exception {
        Notebook nb = NotebookParser.parse("{\"nbformat\":3,\"worksheets\":[{\"cells\":[]}]}");
        NotebookWriter.write(nb);
    }

    @Test
    public void splitLinesMatchesPythonSplitlinesKeepends() {
        assertEquals(0, NotebookWriter.splitLines("").length());
        JSONArray a = NotebookWriter.splitLines("a\nb");
        assertEquals("a\n", a.optString(0));
        assertEquals("b", a.optString(1));
        assertEquals(1, NotebookWriter.splitLines("only\n").length());
    }

    @Test
    public void stringsEscapedLikePythonJson() {
        StringBuilder sb = new StringBuilder();
        NotebookWriter.writeString(sb, "q\"b\\s/t\tn\nc\u0001é😀");
        assertEquals("\"q\\\"b\\\\s/t\\tn\\nc\\u0001é😀\"", sb.toString());
    }

    @Test
    public void numbersFormattedLikePython() {
        assertEquals("5", NotebookWriter.formatNumber(5));
        assertEquals("1.5", NotebookWriter.formatNumber(1.5));
        assertEquals("1.0", NotebookWriter.formatNumber(1.0));
        assertEquals("0.0001", NotebookWriter.formatNumber(0.0001));
        assertEquals("1e-05", NotebookWriter.formatNumber(0.00001));
        assertEquals("10000000.0", NotebookWriter.formatNumber(1e7));
        assertEquals("1e+16", NotebookWriter.formatNumber(1e16));
        assertEquals("-2.5e-07", NotebookWriter.formatNumber(-2.5e-7));
        assertFalse(NotebookWriter.formatNumber(0.1).contains("E"));
    }
}
