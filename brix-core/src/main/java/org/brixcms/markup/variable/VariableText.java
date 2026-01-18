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

package org.brixcms.markup.variable;

import org.brixcms.BrixNodeModel;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.markup.tag.Text;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.request.cycle.RequestCycle;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class VariableText implements Text, VariableKeyProvider {
    private final BrixNodeModel pageNodeModel;
    private final String key;
    private final String pageNodeKey;

    public VariableText(BrixNode pageNode, String key) {
        this.pageNodeModel = new BrixNodeModel(pageNode);
        this.key = key;
        this.pageNodeKey = buildNodeKey(pageNode);
        this.pageNodeModel.detach();
    }


    public String getText() {
        BrixNode node = getPageNode();
        if (node instanceof VariableValueProvider provider) {
            String value = provider.getVariableValue(key);
            return value != null ? value : "[" + key + "]";
        } else {
            return "Couldn't resolve variable '" + key + "'";
        }
    }


    public Collection<String> getVariableKeys() {
        return Arrays.asList(new String[]{key});
    }

    private BrixNode getPageNode() {
        RequestCycle cycle = RequestCycle.get();
        if (cycle != null && pageNodeKey != null) {
            Map<String, BrixNode> cache = cycle.getMetaData(NODE_CACHE_KEY);
            if (cache == null) {
                cache = new HashMap<String, BrixNode>();
                cycle.setMetaData(NODE_CACHE_KEY, cache);
            }
            BrixNode cached = cache.get(pageNodeKey);
            if (cached != null) {
                return cached;
            }
            BrixNode node = new BrixNodeModel(pageNodeModel).getObject();
            cache.put(pageNodeKey, node);
            return node;
        }
        return new BrixNodeModel(pageNodeModel).getObject();
    }

    private String buildNodeKey(BrixNode node) {
        String workspace = node.getSession().getWorkspace().getName();
        String nodeId = node.isNodeType("mix:referenceable") ? node.getIdentifier() : node.getPath();
        return workspace + "-" + nodeId;
    }

    private static final MetaDataKey<Map<String, BrixNode>> NODE_CACHE_KEY =
            new MetaDataKey<Map<String, BrixNode>>() {
            };
}
