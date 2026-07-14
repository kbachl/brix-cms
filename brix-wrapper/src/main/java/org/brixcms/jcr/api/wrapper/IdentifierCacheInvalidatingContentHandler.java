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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

final class IdentifierCacheInvalidatingContentHandler extends XMLFilterImpl {
    private final JcrSession session;
    private boolean cacheSuspended;

    static ContentHandler wrap(ContentHandler delegate, JcrSession session) {
        if (delegate == null) {
            return null;
        }
        return new IdentifierCacheInvalidatingContentHandler(delegate, session);
    }

    private IdentifierCacheInvalidatingContentHandler(ContentHandler delegate, JcrSession session) {
        this.session = session;
        setContentHandler(delegate);
    }

    @Override
    public void startDocument() throws SAXException {
        suspendIdentifierCache();
        super.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            super.endDocument();
        } finally {
            resumeIdentifierCache();
        }
    }

    private void suspendIdentifierCache() {
        if (cacheSuspended) {
            return;
        }
        if (session instanceof SessionWrapper) {
            ((SessionWrapper) session).suspendIdentifierCache();
        } else {
            session.clearIdentifierCache();
        }
        cacheSuspended = true;
    }

    private void resumeIdentifierCache() {
        if (!cacheSuspended) {
            return;
        }
        if (session instanceof SessionWrapper) {
            ((SessionWrapper) session).resumeIdentifierCache();
        } else {
            session.clearIdentifierCache();
        }
        cacheSuspended = false;
    }
}
