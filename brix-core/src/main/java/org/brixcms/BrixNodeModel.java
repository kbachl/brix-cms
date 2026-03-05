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

/**
 *
 */
package org.brixcms;

import java.util.HashSet;
import java.util.Set;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.util.lang.Objects;
import org.brixcms.jcr.JcrUtil;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.wrapper.BrixNode;

public class BrixNodeModel<T extends BrixNode> implements IModel<T> {
    private String id;
    private String workspaceName;
    private transient T node;
    private transient boolean loaded;

    private static final MetaDataKey<Set<String>> MISSING_IDENTIFIER_CACHE_KEY = new MetaDataKey<Set<String>>() {
    };

    public BrixNodeModel() {
        this((T) null);
    }

    public BrixNodeModel(T node) {
        this.node = node;
        this.loaded = true;
        if (node != null) {
            this.id = getId(node);
            this.workspaceName = node.getSession().getWorkspace().getName();
        }
    }

    private String getId(JcrNode node) {
        if (node.isNodeType("mix:referenceable")) {
            return node.getIdentifier();
        } else {
            return node.getPath();
        }
    }

    public BrixNodeModel(BrixNodeModel<?> other) {
        if (other == null) {
            throw new IllegalArgumentException("Argument 'other' may not be null.");
        }
        this.id = other.id;
        this.node = null;
        this.loaded = false;
        this.workspaceName = other.workspaceName;
    }

    public BrixNodeModel(String id, String workspaceName) {
        this.id = id;
        this.node = null;
        this.loaded = false;
        this.workspaceName = workspaceName;
    }

    public String getId() {
        return id;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public String getCacheKey() {
        if (id == null || workspaceName == null) {
            return null;
        }
        return workspaceName + "-" + id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof BrixNodeModel == false) {
            return false;
        }

        BrixNodeModel that = (BrixNodeModel) obj;

        return Objects.equal(this.id, that.id) &&
                Objects.equal(this.workspaceName, that.workspaceName);
    }

    @Override
    public int hashCode() {
        return (id != null ? id.hashCode() : 0) + 33 *
                (workspaceName != null ? workspaceName.hashCode() : 0);
    }



    public void detach() {
        node = null;
        loaded = false;
    }

    public T getObject() {
        if (!loaded) {
            node = loadNode(id);
            loaded = true;
        }
        return node;
    }

    public void setObject(T node) {
        if (node == null) {
            id = null;
            workspaceName = null;
            this.node = null;
            this.loaded = true;
        } else {
            this.node = node;
            this.id = getId(node);
            this.workspaceName = node.getSession().getWorkspace().getName();
            this.loaded = true;
        }
    }

    protected T loadNode(String id) {
        if (id == null) {
            return null;
        }
        JcrSession session = getCurrentSession(workspaceName);
        if (id.startsWith("/")) {
            return loadByPath(session, id);
        }
        return loadByIdentifier(session, id);
    }

    protected JcrSession getCurrentSession(String workspaceName) {
        return Brix.get().getCurrentSession(workspaceName);
    }

    @SuppressWarnings("unchecked")
    protected T loadByPath(JcrSession session, String path) {
        if (!session.nodeExists(path)) {
            return null;
        }
        return (T) session.getNode(path);
    }

    @SuppressWarnings("unchecked")
    protected T loadByIdentifier(JcrSession session, String identifier) {
        if (isKnownMissing(identifier)) {
            return null;
        }
        T resolved = (T) JcrUtil.getNodeByUUID(session, identifier);
        if (resolved == null) {
            rememberMissing(identifier);
        }
        return resolved;
    }

    private boolean isKnownMissing(String identifier) {
        RequestCycle cycle = RequestCycle.get();
        if (cycle == null || workspaceName == null) {
            return false;
        }
        Set<String> missing = cycle.getMetaData(MISSING_IDENTIFIER_CACHE_KEY);
        return missing != null && missing.contains(getMissingIdentifierCacheKey(identifier));
    }

    private void rememberMissing(String identifier) {
        RequestCycle cycle = RequestCycle.get();
        if (cycle == null || workspaceName == null) {
            return;
        }
        Set<String> missing = cycle.getMetaData(MISSING_IDENTIFIER_CACHE_KEY);
        if (missing == null) {
            missing = new HashSet<String>();
            cycle.setMetaData(MISSING_IDENTIFIER_CACHE_KEY, missing);
        }
        missing.add(getMissingIdentifierCacheKey(identifier));
    }

    private String getMissingIdentifierCacheKey(String identifier) {
        return workspaceName + ":" + identifier;
    }
}
