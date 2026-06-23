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

import org.apache.wicket.ThreadContext;
import org.apache.wicket.mock.MockWebRequest;
import org.apache.wicket.mock.MockWebResponse;
import org.apache.wicket.request.IExceptionMapper;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestMapper;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.cycle.RequestCycleContext;
import org.brixcms.Brix;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.api.JcrWorkspace;
import org.easymock.EasyMock;
import org.junit.Test;

public class AbstractContainerVariableCacheTest {
    private static final IRequestMapper NOOP_REQUEST_MAPPER = new IRequestMapper() {
        @Override
        public IRequestHandler mapRequest(Request request) {
            return null;
        }

        @Override
        public int getCompatibilityScore(Request request) {
            return 0;
        }

        @Override
        public Url mapHandler(IRequestHandler requestHandler) {
            return null;
        }
    };

    private static final IExceptionMapper NOOP_EXCEPTION_MAPPER = exception -> null;

    @Test
    public void localVariablesAreLoadedOncePerRequest() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Title");
        container.setLocalVariable("headline", "Headline");

        withRequestCycle(() -> {
            assertEquals("Title", container.getVariableValue("title", false));
            assertEquals("Headline", container.getVariableValue("headline", false));
            assertEquals(new ArrayList<String>(container.variables.keySet()), container.getSavedVariableKeys());
            assertEquals(1, container.localVariableLoads);
        });
    }

    @Test
    public void localVariableCacheIsRequestScoped() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Title");

        withRequestCycle(() -> assertEquals("Title", container.getVariableValue("title", false)));
        withRequestCycle(() -> assertEquals("Title", container.getVariableValue("title", false)));

        assertEquals(2, container.localVariableLoads);
    }

    @Test
    public void setVariableValueInvalidatesCurrentRequestCache() {
        VariableContainer container = new VariableContainer("page-id", "test-workspace");
        container.setLocalVariable("title", "Old");

        withRequestCycle(() -> {
            assertEquals("Old", container.getVariableValue("title", false));
            assertEquals(1, container.localVariableLoads);

            container.setLocalVariable("title", "New");
            container.setVariableValue("title", "New");

            assertEquals("New", container.getVariableValue("title", false));
            assertEquals(2, container.localVariableLoads);
        });
    }

    private static void withRequestCycle(Runnable callback) {
        ThreadContext previous = ThreadContext.detach();
        RequestCycle cycle = new RequestCycle(new RequestCycleContext(
                new MockWebRequest(Url.parse("/")),
                new MockWebResponse(),
                NOOP_REQUEST_MAPPER,
                NOOP_EXCEPTION_MAPPER));
        ThreadContext.setRequestCycle(cycle);
        try {
            callback.run();
        } finally {
            ThreadContext.detach();
            ThreadContext.restore(previous);
        }
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
