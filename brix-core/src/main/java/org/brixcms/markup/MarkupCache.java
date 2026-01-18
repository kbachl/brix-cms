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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contains {@link GeneratedMarkup} instances associated with {@link MarkupContainer}s. The {@link MarkupContainer}s
 * must also implement {@link MarkupSourceProvider} so that the cache can generate markup on demand and reuse it until
 * explicitly invalidated.
 *
 * @author Matej Knopp
 */
public class MarkupCache {
    private Map<String, GeneratedMarkup> map = new ConcurrentHashMap<String, GeneratedMarkup>();

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
        final String key = getKey(container);
        GeneratedMarkup markup = map.get(key);
        if (markup == null) {
            markup = new GeneratedMarkup(provider.getMarkupSource());
            map.put(key, markup);
        }
        return markup;
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
        String suffix = "-" + workspace + "-" + nodeId;
        for (String key : map.keySet()) {
            if (key.endsWith(suffix)) {
                map.remove(key);
            }
        }
    }

    /**
     * Returns the string representation of cache key for the given container.
     *
     * @param container
     * @return
     */
    private String getKey(IGenericComponent<BrixNode> container) {
        BrixNode node = container.getModelObject();
        String nodeId = "";
        if (node != null) {
            nodeId = getNodeId(node);
        }
        String workspace = node.getSession().getWorkspace().getName();
        return container.getClass().getName() + "-" + workspace + "-" + nodeId;
    }

    private String getNodeId(BrixNode node) {
        if (node.isNodeType("mix:referenceable")) {
            return node.getIdentifier();
        }
        return node.getPath();
    }
}
