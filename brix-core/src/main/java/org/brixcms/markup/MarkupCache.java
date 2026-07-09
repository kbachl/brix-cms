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

import org.apache.wicket.MarkupContainer;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.web.generic.IGenericComponent;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Contains {@link GeneratedMarkup} instances associated with {@link MarkupContainer}s. The {@link MarkupContainer}s
 * must also implement {@link MarkupSourceProvider} so that the cache can generate markup on demand and reuse it until
 * explicitly invalidated.
 *
 * @author Matej Knopp
 */
public class MarkupCache {
    private final ConcurrentMap<String, ConcurrentMap<CacheKey, GeneratedMarkup>> workspaceCaches =
            new ConcurrentHashMap<String, ConcurrentMap<CacheKey, GeneratedMarkup>>();

    /**
     * Returns the {@link GeneratedMarkup} instance for given container. The container must implement {@link
     * MarkupSourceProvider}. Markup is regenerated only when explicitly invalidated.
     *
     * @param container
     * @return
     */
    public GeneratedMarkup getMarkup(IGenericComponent<BrixNode> container) {
        if (!(container instanceof MarkupSourceProvider)) {
            throw new IllegalArgumentException("Argument 'container' must implement MarkupSourceProvider");
        }
        MarkupSourceProvider provider = (MarkupSourceProvider) container;
        BrixNode node = container.getModelObject();
        String workspace = node.getSession().getWorkspace().getName();
        ConcurrentMap<CacheKey, GeneratedMarkup> cache = getWorkspaceCache(workspace);
        CacheKey key = getKey(container, node);
        return cache.computeIfAbsent(key, ignored -> new GeneratedMarkup(provider.getMarkupSource()));
    }

    public void invalidate(BrixNode node) {
        if (node == null) {
            return;
        }
        String workspace = node.getSession().getWorkspace().getName();
        String nodeId = getNodeId(node);
        invalidate(workspace, nodeId);
    }

    public void invalidate(String workspace, String nodeId) {
        if (workspace == null || nodeId == null) {
            return;
        }
        ConcurrentMap<CacheKey, GeneratedMarkup> cache = workspaceCaches.get(workspace);
        if (cache != null) {
            cache.keySet().removeIf(key -> nodeId.equals(key.nodeId));
        }
    }

    /**
     * Detaches all generated markup for a workspace. Use this after replacing workspace content through a JCR clone
     * or XML import, because those operations do not emit the node save events used for regular invalidation. Markup
     * generation already in progress can only populate the detached cache and is therefore not visible to later
     * requests.
     *
     * @param workspace workspace whose markup should be discarded
     */
    public void invalidateWorkspace(String workspace) {
        if (workspace == null) {
            return;
        }
        workspaceCaches.remove(workspace);
    }

    private ConcurrentMap<CacheKey, GeneratedMarkup> getWorkspaceCache(String workspace) {
        return workspaceCaches.computeIfAbsent(workspace,
                ignored -> new ConcurrentHashMap<CacheKey, GeneratedMarkup>());
    }

    /**
     * Returns the cache key for the given container within its workspace bucket.
     *
     * @param container
     * @param node
     * @return
     */
    private CacheKey getKey(IGenericComponent<BrixNode> container, BrixNode node) {
        String nodeId = "";
        if (node != null) {
            nodeId = getNodeId(node);
        }
        return new CacheKey(container.getClass().getName(), nodeId);
    }

    private String getNodeId(BrixNode node) {
        if (node.isNodeType("mix:referenceable")) {
            return node.getIdentifier();
        }
        return node.getPath();
    }

    private static class CacheKey {
        private final String componentClass;
        private final String nodeId;

        private CacheKey(String componentClass, String nodeId) {
            this.componentClass = componentClass;
            this.nodeId = nodeId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey key = (CacheKey) other;
            return Objects.equals(componentClass, key.componentClass)
                    && Objects.equals(nodeId, key.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(componentClass, nodeId);
        }
    }
}
