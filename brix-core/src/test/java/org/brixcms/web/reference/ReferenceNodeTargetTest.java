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

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import javax.jcr.Node;

import org.apache.wicket.model.IModel;
import org.brixcms.BrixNodeModel;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.wrapper.BrixNode;
import org.easymock.EasyMock;
import org.junit.Test;

public class ReferenceNodeTargetTest {
    @Test
    public void nodeTargetReturnsResolvedNode() {
        BrixNode expected = createDummyNode();
        Reference reference = new Reference();
        reference.setNodeModel(new StaticNodeModel(expected));

        assertSame(expected, reference.getNodeTarget());
    }

    @Test
    public void missingNodeTargetReturnsNullAndMakesReferenceEmpty() {
        Reference reference = new Reference();
        reference.setNodeModel(new MissingNodeModel());

        assertNull(reference.getNodeTarget());
        assertTrue(reference.isEmpty());
    }

    private static BrixNode createDummyNode() {
        Node delegate = EasyMock.createNiceMock(Node.class);
        JcrSession session = EasyMock.createNiceMock(JcrSession.class);
        return new BrixNode(delegate, session);
    }

    private static class MissingNodeModel extends BrixNodeModel<BrixNode> {
        private MissingNodeModel() {
            super("d5fc9d30-8027-4b8f-8ccc-f5119bb83d47", "test-workspace");
        }

        @Override
        protected JcrSession getCurrentSession(String workspaceName) {
            return null;
        }

        @Override
        protected BrixNode loadByIdentifier(JcrSession session, String identifier) {
            return null;
        }
    }

    private static class StaticNodeModel implements IModel<BrixNode> {
        private final BrixNode node;

        private StaticNodeModel(BrixNode node) {
            this.node = node;
        }

        @Override
        public BrixNode getObject() {
            return node;
        }
    }
}
