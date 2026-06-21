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
package org.brixcms.plugin.site.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ResourceNodePluginTest {

    @Test
    public void legacyJavaScriptTypesNormalizeForStorage() {
        // Browsers still upload .js as application/x-javascript; storage must migrate to text/javascript.
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("application/x-javascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("application/javascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/x-javascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/x-ecmascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("application/ecmascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/ecmascript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/jscript"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/livescript"));
    }

    @Test
    public void versionedJavaScriptNormalizesForStorage() {
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/javascript1.5"));
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/javascript1.7"));
    }

    @Test
    public void canonicalTextJavascriptIsUnchanged() {
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("text/javascript"));
    }

    @Test
    public void nonJavaScriptBaseIsPreservedAndLowercased() {
        assertEquals("text/css", ResourceNodePlugin.normalizeMimeType("text/css"));
        assertEquals("image/png", ResourceNodePlugin.normalizeMimeType("IMAGE/PNG"));
        assertEquals("application/json", ResourceNodePlugin.normalizeMimeType("application/json"));
    }

    @Test
    public void parametersAreStrippedForStorage() {
        // jcr:mimeType stores a bare type; a browser-supplied charset parameter is dropped here.
        assertEquals("text/javascript", ResourceNodePlugin.normalizeMimeType("application/x-javascript; charset=utf-8"));
        assertEquals("text/css", ResourceNodePlugin.normalizeMimeType("text/css; charset=windows-1252"));
    }

    @Test
    public void nullIsPassedThrough() {
        assertNull(ResourceNodePlugin.normalizeMimeType(null));
    }
}
