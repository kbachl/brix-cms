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

package org.brixcms.plugin.site.page;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.jcr.Node;

import org.brixcms.Brix;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.api.JcrWorkspace;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AbstractContainerVariableCacheTest {
    @Before
    public void setUp() {
        AbstractContainer.invalidateAllVariableValues();
    }

    @After
    public void tearDown() {
        AbstractContainer.invalidateAllVariableValues();
    }

    @Test
    public void localVariablesAreLoadedOnceUntilInvalidated() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Title");
        container.setLocalVariable("headline", "Headline");

        assertEquals("Title", container.getVariableValue("title", false));
        assertEquals("Headline", container.getVariableValue("headline", false));
        assertEquals(new ArrayList<String>(container.variables.keySet()), container.getSavedVariableKeys());
        assertEquals("Title", container.getVariableValue("title", false));
        assertEquals(1, container.localVariableLoads);
    }

    @Test
    public void localVariableCacheIsSeparatedByWorkspace() {
        VariableContainer liveContainer = new VariableContainer("page-id", "live-workspace");
        VariableContainer previewContainer = new VariableContainer("page-id", "preview-workspace");
        liveContainer.setLocalVariable("title", "Live");
        previewContainer.setLocalVariable("title", "Preview");

        assertEquals("Live", liveContainer.getVariableValue("title", false));
        assertEquals("Preview", previewContainer.getVariableValue("title", false));
        assertEquals(1, liveContainer.localVariableLoads);
        assertEquals(1, previewContainer.localVariableLoads);
    }

    @Test
    public void workspaceInvalidationClearsOnlyMatchingEntries() {
        VariableContainer liveContainer = new VariableContainer("page-id", "live-workspace");
        VariableContainer previewContainer = new VariableContainer("page-id", "preview-workspace");
        liveContainer.setLocalVariable("title", "Live");
        previewContainer.setLocalVariable("title", "Preview");

        assertEquals("Live", liveContainer.getVariableValue("title", false));
        assertEquals("Preview", previewContainer.getVariableValue("title", false));

        liveContainer.setLocalVariable("title", "Live updated");
        previewContainer.setLocalVariable("title", "Preview updated");
        AbstractContainer.invalidateVariableValuesForWorkspace("live-workspace");

        assertEquals("Live updated", liveContainer.getVariableValue("title", false));
        assertEquals("Preview", previewContainer.getVariableValue("title", false));
        assertEquals(2, liveContainer.localVariableLoads);
        assertEquals(1, previewContainer.localVariableLoads);
    }

    @Test
    public void allInvalidationClearsCachedVariables() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Title");

        assertEquals("Title", container.getVariableValue("title", false));
        container.setLocalVariable("title", "Updated");
        AbstractContainer.invalidateAllVariableValues();

        assertEquals("Updated", container.getVariableValue("title", false));
        assertEquals(2, container.localVariableLoads);
    }

    @Test
    public void setVariableValueInvalidatesCache() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Old");

        assertEquals("Old", container.getVariableValue("title", false));
        assertEquals(1, container.localVariableLoads);

        container.setLocalVariable("title", "New");
        container.setVariableValue("title", "New");

        assertEquals("New", container.getVariableValue("title", false));
        assertEquals(2, container.localVariableLoads);
    }

    private static class VariableContainer extends AbstractContainer {
        private final String identifier;
        private final JcrNode variablesNode = EasyMock.createNiceMock(JcrNode.class);
        private final Map<String, String> variables = new LinkedHashMap<String, String>();
        private int localVariableLoads;

        private VariableContainer(String identifier, String workspaceName) {
            this(identifier, createSession(workspaceName));
        }

        private VariableContainer(String identifier, JcrSession session) {
            super(EasyMock.createNiceMock(Node.class), session);
            this.identifier = identifier;
        }

        private void setLocalVariable(String key, String value) {
            if (value == null) {
                variables.remove(key);
            } else {
                variables.put(key, value);
            }
        }

        @Override
        protected Map<String, String> loadLocalVariableValues() {
            localVariableLoads++;
            return Collections.unmodifiableMap(new LinkedHashMap<String, String>(variables));
        }

        @Override
        public boolean isNodeType(String nodeTypeName) {
            return "mix:referenceable".equals(nodeTypeName);
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }

        @Override
        public boolean hasNode(String relPath) {
            return (Brix.NS_PREFIX + "variables").equals(relPath);
        }

        @Override
        public JcrNode getNode(String relPath) {
            return variablesNode;
        }
    }

    private static JcrSession createSession(String workspaceName) {
        JcrSession session = EasyMock.createMock(JcrSession.class);
        JcrWorkspace workspace = EasyMock.createMock(JcrWorkspace.class);
        EasyMock.expect(session.getWorkspace()).andReturn(workspace).anyTimes();
        EasyMock.expect(workspace.getName()).andReturn(workspaceName).anyTimes();
        EasyMock.replay(session, workspace);
        return session;
    }
}
