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

package org.brixcms.jcr.api.wrapper;

import org.brixcms.jcr.api.JcrSession;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;

final class VersionManagerWrapper implements VersionManager {
    private final VersionManager delegate;
    private final JcrSession session;

    static VersionManager wrap(VersionManager delegate, JcrSession session) {
        if (delegate == null) {
            return null;
        }
        return new VersionManagerWrapper(delegate, session);
    }

    private VersionManagerWrapper(VersionManager delegate, JcrSession session) {
        this.delegate = delegate;
        this.session = session;
    }

    @Override
    public Version checkin(String absPath) throws RepositoryException {
        try {
            return delegate.checkin(absPath);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void checkout(String absPath) throws RepositoryException {
        try {
            delegate.checkout(absPath);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public Version checkpoint(String absPath) throws RepositoryException {
        try {
            return delegate.checkpoint(absPath);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public boolean isCheckedOut(String absPath) throws RepositoryException {
        return delegate.isCheckedOut(absPath);
    }

    @Override
    public VersionHistory getVersionHistory(String absPath) throws RepositoryException {
        return delegate.getVersionHistory(absPath);
    }

    @Override
    public Version getBaseVersion(String absPath) throws RepositoryException {
        return delegate.getBaseVersion(absPath);
    }

    @Override
    public void restore(Version[] versions, boolean removeExisting) throws RepositoryException {
        try {
            delegate.restore(versions, removeExisting);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void restore(String absPath, String versionName, boolean removeExisting) throws RepositoryException {
        try {
            delegate.restore(absPath, versionName, removeExisting);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void restore(Version version, boolean removeExisting) throws RepositoryException {
        try {
            delegate.restore(version, removeExisting);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void restore(String absPath, Version version, boolean removeExisting) throws RepositoryException {
        try {
            delegate.restore(absPath, version, removeExisting);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void restoreByLabel(String absPath, String versionLabel, boolean removeExisting) throws RepositoryException {
        try {
            delegate.restoreByLabel(absPath, versionLabel, removeExisting);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public NodeIterator merge(String absPath, String srcWorkspace, boolean bestEffort) throws RepositoryException {
        try {
            return delegate.merge(absPath, srcWorkspace, bestEffort);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public NodeIterator merge(String absPath, String srcWorkspace, boolean bestEffort, boolean isShallow)
            throws RepositoryException {
        try {
            return delegate.merge(absPath, srcWorkspace, bestEffort, isShallow);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void doneMerge(String absPath, Version version) throws RepositoryException {
        try {
            delegate.doneMerge(absPath, version);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void cancelMerge(String absPath, Version version) throws RepositoryException {
        try {
            delegate.cancelMerge(absPath, version);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public Node createConfiguration(String absPath) throws RepositoryException {
        try {
            return delegate.createConfiguration(absPath);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public Node setActivity(Node activity) throws RepositoryException {
        try {
            return delegate.setActivity(activity);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public Node getActivity() throws RepositoryException {
        return delegate.getActivity();
    }

    @Override
    public Node createActivity(String title) throws RepositoryException {
        try {
            return delegate.createActivity(title);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public void removeActivity(Node activityNode) throws RepositoryException {
        try {
            delegate.removeActivity(activityNode);
        } finally {
            invalidateIdentifierCache();
        }
    }

    @Override
    public NodeIterator merge(Node activityNode) throws RepositoryException {
        try {
            return delegate.merge(activityNode);
        } finally {
            invalidateIdentifierCache();
        }
    }

    private void invalidateIdentifierCache() {
        session.clearIdentifierCache();
    }
}
