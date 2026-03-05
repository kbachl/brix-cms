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

package org.brixcms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import javax.jcr.Node;

import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.wrapper.BrixNode;
import org.easymock.EasyMock;
import org.junit.Test;

public class BrixNodeModelReferenceResolutionTest {
    @Test
    public void validIdentifierLoadsOnceAndCachesNode() {
        BrixNode expected = createDummyNode();
        CountingNodeModel model = new CountingNodeModel("fa899c44-26af-401a-8536-73fe4da7bb99", expected);

        assertSame(expected, model.getObject());
        assertSame(expected, model.getObject());

        assertEquals(1, model.identifierLoads);
        assertEquals(0, model.pathLoads);
    }

    @Test
    public void missingIdentifierReturnsNullWithoutThrowing() {
        CountingNodeModel model = new CountingNodeModel("73836d28-2e74-4f07-ae93-47c002cded53", null);

        assertNull(model.getObject());
        assertNull(model.getObject());

        assertEquals(1, model.identifierLoads);
        assertEquals(0, model.pathLoads);
    }

    @Test
    public void repeatedMissingIdentifierAccessDoesNotReloadUntilDetach() {
        CountingNodeModel model = new CountingNodeModel("73836d28-2e74-4f07-ae93-47c002cded53", null);

        assertNull(model.getObject());
        assertNull(model.getObject());
        assertEquals(1, model.identifierLoads);

        model.detach();
        assertNull(model.getObject());
        assertEquals(2, model.identifierLoads);
    }

    @Test
    public void pathBasedModelUsesPathLoader() {
        BrixNode expected = createDummyNode();
        CountingNodeModel model = new CountingNodeModel("/site/missing-path", expected);

        assertSame(expected, model.getObject());
        assertEquals(0, model.identifierLoads);
        assertEquals(1, model.pathLoads);
    }

    private static BrixNode createDummyNode() {
        Node delegate = EasyMock.createNiceMock(Node.class);
        JcrSession session = EasyMock.createNiceMock(JcrSession.class);
        return new BrixNode(delegate, session);
    }

    private static class CountingNodeModel extends BrixNodeModel<BrixNode> {
        private final BrixNode result;
        private int identifierLoads;
        private int pathLoads;

        private CountingNodeModel(String id, BrixNode result) {
            super(id, "test-workspace");
            this.result = result;
        }

        @Override
        protected JcrSession getCurrentSession(String workspaceName) {
            return null;
        }

        @Override
        protected BrixNode loadByPath(JcrSession session, String path) {
            pathLoads++;
            return result;
        }

        @Override
        protected BrixNode loadByIdentifier(JcrSession session, String identifier) {
            identifierLoads++;
            return result;
        }
    }
}
