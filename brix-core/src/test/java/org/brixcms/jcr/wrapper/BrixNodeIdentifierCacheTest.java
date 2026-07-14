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

package org.brixcms.jcr.wrapper;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;

import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.easymock.EasyMock;
import org.junit.Test;

public class BrixNodeIdentifierCacheTest {
    @Test
    public void changingNodeTypeReloadsTheSpecializedWrapper() throws Exception {
        Session delegateSession = EasyMock.createMock(Session.class);
        Node delegateNode = EasyMock.createMock(Node.class);
        Property delegateProperty = EasyMock.createNiceMock(Property.class);
        ValueFactory delegateValueFactory = EasyMock.createMock(ValueFactory.class);
        Value delegateValue = EasyMock.createNiceMock(Value.class);
        EasyMock.expect(delegateSession.getNodeByIdentifier("identifier")).andReturn(delegateNode).times(2);
        EasyMock.expect(delegateSession.getValueFactory()).andReturn(delegateValueFactory);
        EasyMock.expect(delegateValueFactory.createValue("template")).andReturn(delegateValue);
        EasyMock.expect(delegateNode.isNodeType(BrixNode.JCR_TYPE_BRIX_NODE)).andReturn(true);
        EasyMock.expect(delegateNode.setProperty("brix:nodeType", delegateValue)).andReturn(delegateProperty);
        EasyMock.replay(delegateSession, delegateNode, delegateProperty, delegateValueFactory, delegateValue);

        AtomicBoolean template = new AtomicBoolean();
        JcrSession.Behavior behavior = new JcrSession.Behavior() {
            @Override
            public JcrNode wrap(Node node, JcrSession session) {
                if (template.get()) {
                    return new TemplateNodeWrapper(node, session);
                }
                return new PageNodeWrapper(node, session);
            }

            @Override
            public void nodeSaved(JcrNode node) {
            }

            @Override
            public void handleException(Exception e) {
                throw new RuntimeException(e);
            }
        };
        JcrSession session = JcrSession.Wrapper.wrap(delegateSession, behavior);

        JcrNode page = session.getNodeByIdentifier("identifier");
        assertTrue(page instanceof PageNodeWrapper);

        template.set(true);
        ((BrixNode) page).setNodeType("template");
        JcrNode reloaded = session.getNodeByIdentifier("identifier");

        assertNotSame(page, reloaded);
        assertTrue(reloaded instanceof TemplateNodeWrapper);
        EasyMock.verify(delegateSession, delegateNode, delegateProperty, delegateValueFactory, delegateValue);
    }

    private static class PageNodeWrapper extends BrixNode {
        private PageNodeWrapper(Node delegate, JcrSession session) {
            super(delegate, session);
        }
    }

    private static class TemplateNodeWrapper extends BrixNode {
        private TemplateNodeWrapper(Node delegate, JcrSession session) {
            super(delegate, session);
        }
    }
}
