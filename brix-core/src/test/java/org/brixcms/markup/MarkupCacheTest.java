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

package org.brixcms.markup;

import static org.junit.Assert.assertEquals;

import javax.jcr.Node;

import org.apache.wicket.model.IModel;
import org.brixcms.Brix;
import org.brixcms.auth.AuthorizationStrategy;
import org.brixcms.config.BrixConfig;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.api.JcrWorkspace;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.markup.tag.Item;
import org.brixcms.plugin.site.SitePlugin;
import org.brixcms.web.generic.IGenericComponent;
import org.easymock.EasyMock;
import org.junit.Test;

public class MarkupCacheTest {
    @Test
    public void invalidateWorkspaceRemovesOnlyMarkupFromThatWorkspace() {
        MarkupCache cache = new MarkupCache();
        TestMarkupSource productionSource = new TestMarkupSource();
        TestMarkupSource developmentSource = new TestMarkupSource();
        TestComponent production = new TestComponent(nodeInWorkspace("production", "page-id"), productionSource);
        TestComponent development = new TestComponent(nodeInWorkspace("development", "page-id"), developmentSource);

        cache.getMarkup(production);
        cache.getMarkup(development);
        cache.invalidateWorkspace("production");
        cache.getMarkup(production);
        cache.getMarkup(development);

        assertEquals(2, productionSource.generatedMarkupCount);
        assertEquals(1, developmentSource.generatedMarkupCount);
    }

    @Test
    public void cloneInvalidatesMarkupForTheDestinationWorkspace() {
        TestBrix brix = new TestBrix();
        MarkupCache cache = SitePlugin.get(brix).getMarkupCache();
        TestMarkupSource markupSource = new TestMarkupSource();
        TestComponent component = new TestComponent(nodeInWorkspace("production", "page-id"), markupSource);
        cache.getMarkup(component);

        JcrSession sourceSession = EasyMock.createMock(JcrSession.class);
        JcrSession destinationSession = EasyMock.createMock(JcrSession.class);
        JcrWorkspace sourceWorkspace = EasyMock.createMock(JcrWorkspace.class);
        JcrWorkspace destinationWorkspace = EasyMock.createMock(JcrWorkspace.class);
        EasyMock.expect(destinationSession.itemExists("/brix:root")).andReturn(false);
        destinationSession.save();
        EasyMock.expect(sourceSession.getWorkspace()).andReturn(sourceWorkspace).anyTimes();
        EasyMock.expect(sourceWorkspace.getName()).andReturn("development").anyTimes();
        EasyMock.expect(destinationSession.getWorkspace()).andReturn(destinationWorkspace).anyTimes();
        EasyMock.expect(destinationWorkspace.getName()).andReturn("production").anyTimes();
        destinationWorkspace.clone("development", "/brix:root", "/brix:root", true);
        EasyMock.replay(sourceSession, destinationSession, sourceWorkspace, destinationWorkspace);

        brix.clone(sourceSession, destinationSession);
        cache.getMarkup(component);

        assertEquals(2, markupSource.generatedMarkupCount);
        EasyMock.verify(sourceSession, destinationSession, sourceWorkspace, destinationWorkspace);
    }

    private static BrixNode nodeInWorkspace(String workspaceName, String identifier) {
        JcrSession session = EasyMock.createMock(JcrSession.class);
        JcrWorkspace workspace = EasyMock.createMock(JcrWorkspace.class);
        EasyMock.expect(session.getWorkspace()).andReturn(workspace).anyTimes();
        EasyMock.expect(workspace.getName()).andReturn(workspaceName).anyTimes();
        EasyMock.replay(session, workspace);
        return new TestNode(session, identifier);
    }

    private static class TestBrix extends Brix {
        private TestBrix() {
            super(new BrixConfig(null, null, null));
        }

        @Override
        public AuthorizationStrategy newAuthorizationStrategy() {
            return null;
        }
    }

    private static class TestNode extends BrixNode {
        private final JcrSession session;
        private final String identifier;

        private TestNode(JcrSession session, String identifier) {
            super(EasyMock.createNiceMock(Node.class), session);
            this.session = session;
            this.identifier = identifier;
        }

        @Override
        public JcrSession getSession() {
            return session;
        }

        @Override
        public boolean isNodeType(String nodeTypeName) {
            return "mix:referenceable".equals(nodeTypeName);
        }

        @Override
        public String getIdentifier() {
            return identifier;
        }
    }

    private static class TestComponent implements IGenericComponent<BrixNode>, MarkupSourceProvider {
        private BrixNode node;
        private final MarkupSource markupSource;

        private TestComponent(BrixNode node, MarkupSource markupSource) {
            this.node = node;
            this.markupSource = markupSource;
        }

        @Override
        public IModel<BrixNode> getModel() {
            return null;
        }

        @Override
        public BrixNode getModelObject() {
            return node;
        }

        @Override
        public void setModel(IModel<BrixNode> model) {
            node = model.getObject();
        }

        @Override
        public void setModelObject(BrixNode object) {
            node = object;
        }

        @Override
        public MarkupSource getMarkupSource() {
            return markupSource;
        }
    }

    private static class TestMarkupSource implements MarkupSource {
        private int generatedMarkupCount;

        @Override
        public String getDoctype() {
            return null;
        }

        @Override
        public Object getExpirationToken() {
            generatedMarkupCount++;
            return new Object();
        }

        @Override
        public boolean isMarkupExpired(Object expirationToken) {
            return false;
        }

        @Override
        public Item nextMarkupItem() {
            return null;
        }
    }
}
