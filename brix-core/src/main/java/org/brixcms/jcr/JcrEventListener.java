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

package org.brixcms.jcr;

import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrNodeIterator;
import org.brixcms.jcr.api.JcrProperty;
import org.brixcms.jcr.base.SaveEvent;
import org.brixcms.jcr.base.SaveEventListener;
import org.brixcms.jcr.wrapper.BrixFileNode;
import org.brixcms.jcr.wrapper.BrixNode;

import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;

public class JcrEventListener implements SaveEventListener {

    public void onEvent(EventIterator events) {
        while (events.hasNext()) {
            handleEvent(events.nextEvent());
        }
    }

    private void handleEvent(Event event) {
        if (event instanceof SaveEvent saveEvent) {
            handleSaveEvent(saveEvent);
        }
    }

    private void handleSaveEvent(SaveEvent event) {
        JcrNode node = event.getNode();
        touch(node);
        touchContentRevision(node);
        if (node.getDepth() == 0 || node.isNodeType("nt:folder")) {
            // go through immediate children to determine if there are any
            // modified ones
            JcrNodeIterator iterator = node.getNodes();
            while (iterator.hasNext()) {
                JcrNode n = iterator.nextNode();
                if (n.isModified() || n.isNew()) {
                    touch(n);
                    touchContentRevision(n);
                } else if (n.hasNode("jcr:content")) {
                    JcrNode content = n.getNode("jcr:content");
                    if (content.isModified() || content.isNew()) {
                        touch(n);
                        touchContentRevision(n);
                    } else if (content.hasProperty("jcr:data")) {
                        JcrProperty data = content.getProperty("jcr:data");
                        if (data.isNew() || data.isModified()) {
                            touch(n);
                            touchContentRevision(n);
                        }
                    }
                }
            }
        }
    }

    private void touch(JcrNode node) {
        if (node.isNodeType("nt:file") || node.isNodeType("nt:folder")) {
            new BrixNode(node.getDelegate(), node.getSession()).touch();
        }
    }

    private void touchContentRevision(JcrNode node) {
        if (!node.isNodeType("nt:file") || !node.hasNode("jcr:content")) {
            return;
        }
        JcrNode content = node.getNode("jcr:content");
        if (!content.hasProperty("jcr:data")) {
            return;
        }
        JcrProperty data = content.getProperty("jcr:data");
        if (!data.isNew() && !data.isModified()) {
            return;
        }
        JcrProperty lastModified = content.hasProperty("jcr:lastModified")
                ? content.getProperty("jcr:lastModified") : null;
        if (lastModified == null || (!lastModified.isNew() && !lastModified.isModified())) {
            new BrixFileNode(node.getDelegate(), node.getSession()).touchContentLastModified();
        }
    }
}
