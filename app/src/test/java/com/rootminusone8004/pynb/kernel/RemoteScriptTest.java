package com.rootminusone8004.pynb.kernel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RemoteScriptTest {

    @Test
    public void shellQuoteEscapesSingleQuotes() {
        assertEquals("'it'\\''s'", RemoteScript.shellQuote("it's"));
        assertEquals("'$HOME; rm -rf /'", RemoteScript.shellQuote("$HOME; rm -rf /"));
    }

    @Test
    public void remotePathExpandsHomeOnly() {
        assertEquals("\"$HOME\"", RemoteScript.remotePath("~"));
        assertEquals("\"$HOME\"/'proj/.venv'", RemoteScript.remotePath(" ~/proj/.venv/ "));
        assertEquals("'/opt/env'", RemoteScript.remotePath("/opt/env"));
        assertEquals("'~user/x'", RemoteScript.remotePath("~user/x"));
    }

    @Test
    public void parentOf() {
        assertEquals("~/proj", RemoteScript.parentOf("~/proj/.venv"));
        assertEquals("/", RemoteScript.parentOf("/venv"));
        assertEquals("~", RemoteScript.parentOf(".venv"));
    }

    @Test
    public void startScriptKeepsSecretsOffCommandLineAndUsesUniqueDelimiter() {
        String src = "print('PYNB_EOF_')\n";
        String s = RemoteScript.startScript("~/p/.venv", "", src, "abc123", "5e55");
        assertTrue(s.contains("py=\"$HOME\"/'p/.venv'/bin/python"));
        assertTrue(s.contains("cd \"$HOME\"/'p'"));
        assertTrue(s.contains("printf '%s' 'abc123' > \"$d\"/5e55.token"));
        assertTrue(s.contains("--token-file \"$d\"/5e55.token"));
        assertFalse(s.contains("--token abc123"));
        assertTrue(s.contains(src));
    }

    @Test
    public void startScriptRejectsNonHexIds() {
        try {
            RemoteScript.startScript("~/v", "", "", "abc; rm", "1");
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void parsePort() {
        assertEquals(41234, RemoteScript.parsePort("motd\nPYNB_PORT 41234\n"));
        try {
            RemoteScript.parsePort("PYNB_ERR No Python at ~/v/bin/python\n");
            fail();
        } catch (IllegalStateException e) {
            assertEquals("No Python at ~/v/bin/python", e.getMessage());
        }
    }
}
