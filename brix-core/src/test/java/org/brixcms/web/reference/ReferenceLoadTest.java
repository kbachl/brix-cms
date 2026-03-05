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

package org.brixcms.web.reference;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;

import org.brixcms.BrixNodeModel;
import org.brixcms.jcr.api.JcrProperty;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.api.JcrWorkspace;
import org.brixcms.jcr.wrapper.BrixNode;
import org.easymock.EasyMock;
import org.junit.Test;

public class ReferenceLoadTest {
    @Test
    public void loadUsesIdentifierStringInsteadOfDereferencingNodeProperty() {
        JcrProperty typeProperty = EasyMock.createMock(JcrProperty.class);
        JcrProperty nodeProperty = EasyMock.createMock(JcrProperty.class);
        JcrSession session = EasyMock.createMock(JcrSession.class);
        JcrWorkspace workspace = EasyMock.createMock(JcrWorkspace.class);
        StubBrixNode source = new StubBrixNode(session);
        source.putProperty("type", typeProperty);
        source.putProperty("node", nodeProperty);

        EasyMock.expect(typeProperty.getString()).andReturn("NODE");
        EasyMock.expect(nodeProperty.getString()).andReturn("fa899c44-26af-401a-8536-73fe4da7bb99");

        EasyMock.expect(session.getWorkspace()).andReturn(workspace);
        EasyMock.expect(workspace.getName()).andReturn("live");

        EasyMock.replay(typeProperty, nodeProperty, session, workspace);

        Reference reference = new Reference();
        reference.load(source);

        BrixNodeModel<?> model = (BrixNodeModel<?>) reference.getNodeModel();
        assertEquals("fa899c44-26af-401a-8536-73fe4da7bb99", model.getId());
        assertEquals("live", model.getWorkspaceName());

        EasyMock.verify(typeProperty, nodeProperty, session, workspace);
    }

    private static class StubBrixNode extends BrixNode {
        private final Map<String, JcrProperty> properties = new HashMap<String, JcrProperty>();
        private final JcrSession session;

        private StubBrixNode(JcrSession session) {
            super(EasyMock.createNiceMock(Node.class), session);
            this.session = session;
        }

        private void putProperty(String name, JcrProperty property) {
            properties.put(name, property);
        }

        @Override
        public boolean hasProperty(String relPath) {
            return properties.containsKey(relPath);
        }

        @Override
        public JcrProperty getProperty(String relPath) {
            return properties.get(relPath);
        }

        @Override
        public boolean hasNode(String relPath) {
            return false;
        }

        @Override
        public JcrSession getSession() {
            return session;
        }
    }
}
