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

package org.brixcms.workspace.rmi;

import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientWorkspaceTest {
    @Test
    public void equalityResolvesLazyWorkspaceIds() throws Exception {
        RemoteWorkspace first = workspaceWithId("first");
        RemoteWorkspace second = workspaceWithId("second");

        ClientWorkspace firstWorkspace = new ClientWorkspace(first);
        ClientWorkspace secondWorkspace = new ClientWorkspace(second);

        assertFalse(firstWorkspace.equals(secondWorkspace));
        assertTrue(firstWorkspace.equals(firstWorkspace));
        EasyMock.verify(first, second);
    }

    @Test
    public void equalIdsHaveEqualHashCodes() throws Exception {
        RemoteWorkspace first = workspaceWithId("same");
        RemoteWorkspace second = workspaceWithId("same");

        ClientWorkspace firstWorkspace = new ClientWorkspace(first);
        ClientWorkspace secondWorkspace = new ClientWorkspace(second);

        assertTrue(firstWorkspace.equals(secondWorkspace));
        assertEquals(firstWorkspace.hashCode(), secondWorkspace.hashCode());
        EasyMock.verify(first, second);
    }

    private RemoteWorkspace workspaceWithId(String id) throws Exception {
        RemoteWorkspace workspace = EasyMock.createMock(RemoteWorkspace.class);
        EasyMock.expect(workspace.getId()).andReturn(id);
        EasyMock.replay(workspace);
        return workspace;
    }
}
