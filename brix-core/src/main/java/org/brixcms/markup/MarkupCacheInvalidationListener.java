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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

import org.brixcms.Brix;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrProperty;
import org.brixcms.jcr.api.JcrPropertyIterator;
import org.brixcms.jcr.base.SaveEvent;
import org.brixcms.jcr.base.SaveEventListener;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.plugin.site.SitePlugin;
import org.brixcms.plugin.site.page.AbstractContainer;
import org.brixcms.plugin.site.page.PageSiteNodePlugin;
import org.brixcms.plugin.site.page.TemplateNode;
import org.brixcms.plugin.site.page.TemplateSiteNodePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkupCacheInvalidationListener implements SaveEventListener {
    private static final Logger log = LoggerFactory.getLogger(MarkupCacheInvalidationListener.class);
    private static final String TEMPLATE_PROPERTY = Brix.NS_PREFIX + "template";

    private final Brix brix;

    public MarkupCacheInvalidationListener(Brix brix) {
        if (brix == null) {
            throw new IllegalArgumentException("brix may not be null");
        }
        this.brix = brix;
    }

    @Override
    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            Event event = events.nextEvent();
            if (event instanceof SaveEvent saveEvent) {
                invalidateForNode(saveEvent.getNode());
            }
        }
    }

    private void invalidateForNode(JcrNode node) {
        if (node == null) {
            return;
        }
        try {
            JcrNode container = resolveContainerNode(node);
            if (container == null) {
                return;
            }
            SitePlugin plugin = SitePlugin.get(brix);
            if (plugin == null) {
                return;
            }
            invalidateContainerAndDependents(plugin.getMarkupCache(), container);
        } catch (RuntimeException e) {
            log.debug("Failed to invalidate markup cache for {}", safePath(node), e);
        }
    }

    private JcrNode resolveContainerNode(JcrNode node) {
        if (isContainerNode(node)) {
            return node;
        }
        if (!"jcr:content".equals(node.getName())) {
            return null;
        }
        JcrNode parent = node.getParent();
        if (parent != null && isContainerNode(parent)) {
            return parent;
        }
        return null;
    }

    private void invalidateContainerAndDependents(MarkupCache cache, JcrNode container) {
        Deque<JcrNode> queue = new ArrayDeque<JcrNode>();
        Set<String> visited = new HashSet<String>();
        queue.add(container);

        while (!queue.isEmpty()) {
            JcrNode current = queue.removeFirst();
            String nodeKey = getNodeKey(current);
            if (nodeKey != null && !visited.add(nodeKey)) {
                continue;
            }
            cache.invalidate(toBrixNode(current));

            if (isTemplateNode(current)) {
                enqueueReferencingContainers(current, queue);
            }
        }
    }

    private void enqueueReferencingContainers(JcrNode template, Deque<JcrNode> queue) {
        JcrPropertyIterator refs = template.getReferences(TEMPLATE_PROPERTY);
        while (refs.hasNext()) {
            JcrProperty property = refs.nextProperty();
            Node parent = property.getParent();
            JcrNode refNode = JcrNode.Wrapper.wrap(parent, template.getSession());
            if (isContainerNode(refNode)) {
                queue.add(refNode);
            }
        }
    }

    private boolean isContainerNode(JcrNode node) {
        if (node instanceof AbstractContainer) {
            return true;
        }
        String type = BrixNode.getNodeType(node);
        return PageSiteNodePlugin.TYPE.equals(type) || TemplateSiteNodePlugin.TYPE.equals(type);
    }

    private boolean isTemplateNode(JcrNode node) {
        if (node instanceof TemplateNode) {
            return true;
        }
        String type = BrixNode.getNodeType(node);
        return TemplateSiteNodePlugin.TYPE.equals(type);
    }

    private BrixNode toBrixNode(JcrNode node) {
        if (node instanceof BrixNode brixNode) {
            return brixNode;
        }
        return new BrixNode(node.getDelegate(), node.getSession());
    }

    private String getNodeKey(JcrNode node) {
        if (node == null) {
            return null;
        }
        String workspace = node.getSession().getWorkspace().getName();
        String nodeId = node.isNodeType("mix:referenceable") ? node.getIdentifier() : node.getPath();
        return workspace + "-" + nodeId;
    }

    private String safePath(JcrNode node) {
        try {
            return node.getPath();
        } catch (RuntimeException e) {
            return "(unknown)";
        }
    }
}
