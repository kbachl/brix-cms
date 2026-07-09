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

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains {@link GeneratedMarkup} instances associated with {@link MarkupContainer}s. The {@link MarkupContainer}s
 * must also implement {@link MarkupSourceProvider} so that the cache can generate markup on demand and reuse it until
 * explicitly invalidated.
 *
 * @author Matej Knopp
 */
public class MarkupCache {
    private final Map<CacheKey, GeneratedMarkup> map = new ConcurrentHashMap<CacheKey, GeneratedMarkup>();

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
        final CacheKey key = getKey(container);
        return map.computeIfAbsent(key, ignored -> new GeneratedMarkup(provider.getMarkupSource()));
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
        map.keySet().removeIf(key -> workspace.equals(key.workspace) && nodeId.equals(key.nodeId));
    }

    /**
     * Removes all generated markup for a workspace. Use this after replacing workspace content through a JCR clone or
     * XML import, because those operations do not emit the node save events used for regular invalidation.
     *
     * @param workspace workspace whose markup should be discarded
     */
    public void invalidateWorkspace(String workspace) {
        if (workspace == null) {
            return;
        }
        map.keySet().removeIf(key -> workspace.equals(key.workspace));
    }

    /**
     * Returns the string representation of cache key for the given container.
     *
     * @param container
     * @return
     */
    private CacheKey getKey(IGenericComponent<BrixNode> container) {
        BrixNode node = container.getModelObject();
        String nodeId = "";
        if (node != null) {
            nodeId = getNodeId(node);
        }
        String workspace = node.getSession().getWorkspace().getName();
        return new CacheKey(container.getClass().getName(), workspace, nodeId);
    }

    private String getNodeId(BrixNode node) {
        if (node.isNodeType("mix:referenceable")) {
            return node.getIdentifier();
        }
        return node.getPath();
    }

    private static class CacheKey {
        private final String componentClass;
        private final String workspace;
        private final String nodeId;

        private CacheKey(String componentClass, String workspace, String nodeId) {
            this.componentClass = componentClass;
            this.workspace = workspace;
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
                    && Objects.equals(workspace, key.workspace)
                    && Objects.equals(nodeId, key.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(componentClass, workspace, nodeId);
        }
    }
}
