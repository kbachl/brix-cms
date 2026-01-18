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

package org.brixcms.jcr.base.event;

import org.junit.Test;

import javax.jcr.Node;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChangeLogPathCacheTest {
    @Test
    public void testNodeEventPathRefreshOnMismatch() throws Exception {
        AtomicReference<String> path = new AtomicReference<String>("/a/x");
        Node node = newPathNode(path);

        ChangeLog changeLog = new ChangeLog();
        changeLog.addEvent(new TestNodeEvent(node));

        List<Event> first = changeLog.removeAndGetAffectedEvents("/c");
        assertTrue(first.isEmpty());

        path.set("/b/x");
        List<Event> second = changeLog.removeAndGetAffectedEvents("/b");
        assertEquals(1, second.size());
    }

    @Test
    public void testPropertyEventPathRefreshOnMismatch() throws Exception {
        AtomicReference<String> path = new AtomicReference<String>("/a");
        Node node = newPathNode(path);

        ChangeLog changeLog = new ChangeLog();
        changeLog.addEvent(new TestPropertyEvent(node, "p"));

        List<Event> first = changeLog.removeAndGetAffectedEvents("/c");
        assertTrue(first.isEmpty());

        path.set("/b");
        List<Event> second = changeLog.removeAndGetAffectedEvents("/b");
        assertEquals(1, second.size());
    }

    private static Node newPathNode(final AtomicReference<String> path) {
        InvocationHandler handler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                if ("getPath".equals(name)) {
                    return path.get();
                }
                if ("toString".equals(name)) {
                    return "Node[" + path.get() + "]";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == args[0];
                }
                throw new UnsupportedOperationException(name);
            }
        };

        return (Node) Proxy.newProxyInstance(
                ChangeLogPathCacheTest.class.getClassLoader(),
                new Class<?>[]{Node.class},
                handler);
    }

    private static final class TestNodeEvent extends NodeEvent {
        TestNodeEvent(Node node) {
            super(node);
        }
    }

    private static final class TestPropertyEvent extends PropertyEvent {
        TestPropertyEvent(Node node, String propertyName) {
            super(node, propertyName);
        }
    }
}
