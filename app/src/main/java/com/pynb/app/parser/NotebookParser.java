package com.pynb.app.parser;

import com.pynb.app.model.CellOutput;
import com.pynb.app.model.Notebook;
import com.pynb.app.model.NotebookCell;
import com.pynb.app.model.NotebookMetadata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Robust JSON parser for Jupyter Notebooks (.ipynb), supporting nbformat v4 and v3.
 */
public class NotebookParser {

    public static Notebook parse(InputStream inputStream) throws IOException, JSONException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
        }
        return parse(sb.toString());
    }

    public static Notebook parse(String jsonString) throws JSONException {
        if (jsonString == null) {
            throw new JSONException("JSON input is null");
        }
        if (jsonString.startsWith("\uFEFF")) {
            jsonString = jsonString.substring(1);
        }
        jsonString = jsonString.trim();

        JSONObject root = new JSONObject(jsonString);
        Notebook notebook = new Notebook();

        int nbformat = root.optInt("nbformat", 4);
        int nbformatMinor = root.optInt("nbformat_minor", 0);
        notebook.setNbformat(nbformat);
        notebook.setNbformatMinor(nbformatMinor);

        // Parse metadata
        NotebookMetadata metadata = new NotebookMetadata();
        JSONObject metaObj = root.optJSONObject("metadata");
        if (metaObj != null) {
            JSONObject kernelSpec = metaObj.optJSONObject("kernelspec");
            if (kernelSpec != null) {
                metadata.setKernelName(kernelSpec.optString("name", "python3"));
                metadata.setKernelDisplayName(kernelSpec.optString("display_name", "Python 3"));
            }
            JSONObject langInfo = metaObj.optJSONObject("language_info");
            if (langInfo != null) {
                metadata.setLanguageName(langInfo.optString("name", "python"));
                metadata.setLanguageVersion(langInfo.optString("version", ""));
            }
        }
        notebook.setMetadata(metadata);

        List<NotebookCell> cells = new ArrayList<>();

        // nbformat 4: "cells" at root
        if (root.has("cells")) {
            JSONArray cellsArray = root.getJSONArray("cells");
            for (int i = 0; i < cellsArray.length(); i++) {
                JSONObject cellObj = cellsArray.getJSONObject(i);
                NotebookCell cell = parseCell(cellObj);
                cells.add(cell);
            }
        } else if (root.has("worksheets")) {
            // nbformat 3: "worksheets" array
            JSONArray worksheets = root.getJSONArray("worksheets");
            for (int w = 0; w < worksheets.length(); w++) {
                JSONObject ws = worksheets.getJSONObject(w);
                if (ws.has("cells")) {
                    JSONArray wsCells = ws.getJSONArray("cells");
                    for (int i = 0; i < wsCells.length(); i++) {
                        JSONObject cellObj = wsCells.getJSONObject(i);
                        NotebookCell cell = parseCell(cellObj);
                        cells.add(cell);
                    }
                }
            }
        }

        notebook.setCells(cells);
        return notebook;
    }

    private static NotebookCell parseCell(JSONObject cellObj) {
        NotebookCell cell = new NotebookCell();
        String cellType = cellObj.optString("cell_type", "code");
        cell.setCellType(cellType);

        // Source can be string or array of strings (or "input" in v3)
        String source = "";
        if (cellObj.has("source")) {
            source = extractStringOrArray(cellObj.opt("source"));
        } else if (cellObj.has("input")) {
            source = extractStringOrArray(cellObj.opt("input"));
        }
        cell.setSource(source);

        // Execution count (or "prompt_number" in v3)
        if (cellObj.has("execution_count") && !cellObj.isNull("execution_count")) {
            cell.setExecutionCount(cellObj.optInt("execution_count"));
        } else if (cellObj.has("prompt_number") && !cellObj.isNull("prompt_number")) {
            cell.setExecutionCount(cellObj.optInt("prompt_number"));
        }

        // Outputs
        List<CellOutput> outputs = new ArrayList<>();
        if (cellObj.has("outputs")) {
            JSONArray outputsArray = cellObj.optJSONArray("outputs");
            if (outputsArray != null) {
                for (int j = 0; j < outputsArray.length(); j++) {
                    JSONObject outObj = outputsArray.optJSONObject(j);
                    if (outObj != null) {
                        CellOutput output = parseOutput(outObj);
                        outputs.add(output);
                    }
                }
            }
        }
        cell.setOutputs(outputs);

        return cell;
    }

    private static CellOutput parseOutput(JSONObject outObj) {
        CellOutput output = new CellOutput();
        String outputType = outObj.optString("output_type", "");
        output.setOutputType(outputType);

        if (outObj.has("name")) {
            output.setName(outObj.optString("name"));
        }

        if (outObj.has("execution_count") && !outObj.isNull("execution_count")) {
            output.setExecutionCount(outObj.optInt("execution_count"));
        } else if (outObj.has("prompt_number") && !outObj.isNull("prompt_number")) {
            output.setExecutionCount(outObj.optInt("prompt_number"));
        }

        if (outObj.has("text")) {
            output.setText(extractStringOrArray(outObj.opt("text")));
        }

        if (outObj.has("ename")) {
            output.setEname(outObj.optString("ename"));
        }
        if (outObj.has("evalue")) {
            output.setEvalue(outObj.optString("evalue"));
        }

        if (outObj.has("traceback")) {
            JSONArray tbArray = outObj.optJSONArray("traceback");
            if (tbArray != null) {
                List<String> tbList = new ArrayList<>();
                for (int t = 0; t < tbArray.length(); t++) {
                    tbList.add(tbArray.optString(t));
                }
                output.setTraceback(tbList);
            }
        }

        // Data map for display_data / execute_result
        if (outObj.has("data")) {
            JSONObject dataObj = outObj.optJSONObject("data");
            if (dataObj != null) {
                Map<String, String> dataMap = new HashMap<>();
                Iterator<String> keys = dataObj.keys();
                while (keys.hasNext()) {
                    String mime = keys.next();
                    Object val = dataObj.opt(mime);
                    dataMap.put(mime, extractStringOrArray(val));
                }
                output.setData(dataMap);
            }
        }

        return output;
    }

    private static String extractStringOrArray(Object obj) {
        if (obj == null) {
            return "";
        }
        if (obj instanceof String) {
            return (String) obj;
        }
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                sb.append(array.optString(i));
            }
            return sb.toString();
        }
        return obj.toString();
    }
}
