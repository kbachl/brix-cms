/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brixcms.web.nodepage.toolbar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ToolbarBehaviorTest {
    @Test
    public void escapesJavaScriptStringDelimitersAndControlCharacters() {
        assertEquals("O\\'Brien", ToolbarBehavior.escapeJavaScriptString("O'Brien"));
        assertEquals("C:\\\\temp", ToolbarBehavior.escapeJavaScriptString("C:\\temp"));
        assertEquals("line 1\\nline 2\\r\\t", ToolbarBehavior.escapeJavaScriptString("line 1\nline 2\r\t"));
        assertEquals("\\u0001", ToolbarBehavior.escapeJavaScriptString("\u0001"));
    }

    @Test
    public void preventsScriptElementTermination() {
        assertEquals("\\u003c/script\\u003e\\u0026",
                ToolbarBehavior.escapeJavaScriptString("</script>&"));
        assertEquals("\\u2028\\u2029", ToolbarBehavior.escapeJavaScriptString("\u2028\u2029"));
    }

    @Test
    public void rendersWorkspaceNamesAsTextInsteadOfHtml() throws IOException {
        assertEquals("\\u003cb\\u003eprod\\u003c/b\\u003e \\u0026lt;live\\u0026gt;",
                ToolbarBehavior.escapeJavaScriptString("<b>prod</b> &lt;live&gt;"));

        try (InputStream stream = ToolbarBehavior.class.getResourceAsStream("toolbar.js")) {
            assertNotNull(stream);
            String script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(script.contains("o.textContent = rev.name;"));
            assertFalse(script.contains("o.innerHTML = rev.name;"));
        }
    }
}
