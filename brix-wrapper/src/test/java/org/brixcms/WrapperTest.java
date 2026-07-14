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

package org.brixcms;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.exception.JcrException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.jcr.Credentials;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.SimpleCredentials;
import javax.jcr.version.Version;
import javax.jcr.version.VersionManager;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WrapperTest {
    private static final Logger logger = LoggerFactory.getLogger(WrapperTest.class);

    private Repository repo;
    private List<JcrSession> sessions;

    private File home;

    @After
    public void cleanup() {
        for (JcrSession session : sessions) {
            if (session.isLive()) {
                session.logout();
            }
        }
        ((JackrabbitRepository) repo).shutdown();

        delete(home);
    }

    @Before
    public void setupManager() throws IOException, RepositoryException {
        String temp = System.getProperty("java.io.tmpdir");
        home = new File(temp, getClass().getName());
        delete(home);
        home.deleteOnExit();

        if (!home.mkdirs()) {
            throw new RuntimeException("Could not create directory: " + home.getAbsolutePath());
        }

        InputStream configStream = getClass().getResourceAsStream("repository.xml");
        RepositoryConfig config = RepositoryConfig.create(configStream, home.getAbsolutePath());
        repo = RepositoryImpl.create(config);

        logger.info("Initializer Jackrabbit Repository in: " + home.getAbsolutePath());

        sessions = new ArrayList<JcrSession>();
    }

    private static void delete(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        if (!file.delete()) {
            throw new RuntimeException("Could not delete file: " + file.getAbsolutePath());
        }
    }

    @Test
    public void testgetNodeByIdentifier() throws RepositoryException {
        JcrSession session = login();

        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);

        assertNotNull(node.getIdentifier());

        JcrNode node1 = session.getNodeByIdentifier(node.getIdentifier());
        assertNotNull(node1);
        node1.setProperty("property", "value");
    }

    @Test
    public void identifierLookupReusesCachedWrapper() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();

        JcrNode first = session.getNodeByIdentifier(node.getIdentifier());
        JcrNode second = session.getNodeByIdentifier(node.getIdentifier());

        assertSame(first, second);
    }

    @Test
    public void identifierCacheRetainsMoreThan1024Wrappers() throws RepositoryException {
        JcrSession session = login();
        List<String> identifiers = new ArrayList<>();
        for (int i = 0; i < 1025; i++) {
            JcrNode node = session.getRootNode().addNode("node" + i);
            node.addMixin(JcrConstants.MIX_REFERENCEABLE);
            identifiers.add(node.getIdentifier());
        }
        session.save();

        JcrNode first = session.getNodeByIdentifier(identifiers.get(0));
        for (int i = 1; i < identifiers.size(); i++) {
            session.getNodeByIdentifier(identifiers.get(i));
        }

        assertSame(first, session.getNodeByIdentifier(identifiers.get(0)));
    }

    @Test
    public void setPrimaryTypeInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node", "nt:unstructured");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        String identifier = node.getIdentifier();
        JcrNode beforeTypeChange = session.getNodeByIdentifier(identifier);

        beforeTypeChange.setPrimaryType("nt:folder");
        JcrNode afterTypeChange = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeTypeChange, afterTypeChange);
        assertEquals("nt:folder", afterTypeChange.getPrimaryNodeType().getName());
    }

    @Test
    public void addMixinInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        String identifier = createReferenceableNode(session);
        JcrNode beforeMixin = session.getNodeByIdentifier(identifier);

        beforeMixin.addMixin("mix:lockable");
        JcrNode afterMixin = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeMixin, afterMixin);
        assertTrue(afterMixin.isNodeType("mix:lockable"));
    }

    @Test
    public void removeMixinInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        node.addMixin("mix:lockable");
        session.save();
        String identifier = node.getIdentifier();
        JcrNode beforeMixinRemoval = session.getNodeByIdentifier(identifier);

        beforeMixinRemoval.removeMixin("mix:lockable");
        JcrNode afterMixinRemoval = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeMixinRemoval, afterMixinRemoval);
        assertFalse(afterMixinRemoval.isNodeType("mix:lockable"));
    }

    @Test
    public void sessionMoveInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        String identifier = node.getIdentifier();
        JcrNode beforeMove = session.getNodeByIdentifier(identifier);
        assertEquals("/node", beforeMove.getPath());

        session.move("/node", "/renamed");
        JcrNode afterMove = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeMove, afterMove);
        assertEquals("/renamed", afterMove.getPath());
    }

    @Test
    public void itemRefreshInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        String identifier = node.getIdentifier();
        JcrNode beforeRefresh = session.getNodeByIdentifier(identifier);

        beforeRefresh.refresh(false);
        JcrNode afterRefresh = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeRefresh, afterRefresh);
    }

    @Test
    public void nodeRemovalInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        String identifier = node.getIdentifier();
        JcrNode cached = session.getNodeByIdentifier(identifier);

        cached.remove();

        try {
            session.getNodeByIdentifier(identifier);
            fail("Removed node must not be returned from the identifier cache");
        } catch (JcrException expected) {
            // The delegate must be consulted after removal and report the missing node.
        }
    }

    @Test
    public void workspaceMoveInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        String identifier = node.getIdentifier();
        JcrNode beforeMove = session.getNodeByIdentifier(identifier);
        assertEquals("/node", beforeMove.getPath());

        session.getWorkspace().move("/node", "/renamed");
        JcrNode afterMove = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeMove, afterMove);
        assertEquals("/renamed", afterMove.getPath());
    }

    @Test
    public void versionManagerRestoreInvalidatesIdentifierCache() throws RepositoryException {
        JcrSession session = login();
        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        node.addMixin(JcrConstants.MIX_VERSIONABLE);
        node.setProperty("value", "first");
        session.save();
        String identifier = node.getIdentifier();

        VersionManager versionManager = session.getWorkspace().getVersionManager();
        Version firstVersion = versionManager.checkpoint("/node");
        node.setProperty("value", "second");
        session.save();
        versionManager.checkpoint("/node");
        JcrNode beforeRestore = session.getNodeByIdentifier(identifier);
        assertEquals("second", beforeRestore.getProperty("value").getString());

        versionManager.restore(firstVersion, true);
        JcrNode afterRestore = session.getNodeByIdentifier(identifier);

        assertNotSame(beforeRestore, afterRestore);
        assertEquals("first", afterRestore.getProperty("value").getString());
    }

    @Test
    public void sessionImportContentHandlerInvalidatesIdentifierCacheOnCompletion() throws Exception {
        JcrSession session = login();
        String identifier = createReferenceableNode(session);
        ContentHandler importHandler = session.getImportContentHandler("/", ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);

        JcrNode cachedDuringImport = importNodeAndCacheAfterStart(session, identifier, importHandler,
                "session-import");
        JcrNode afterImport = session.getNodeByIdentifier(identifier);

        assertNotSame(cachedDuringImport, afterImport);
    }

    @Test
    public void workspaceImportContentHandlerInvalidatesIdentifierCacheOnCompletion() throws Exception {
        JcrSession session = login();
        String identifier = createReferenceableNode(session);
        ContentHandler importHandler = session.getWorkspace().getImportContentHandler("/",
                ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);

        JcrNode cachedDuringImport = importNodeAndCacheAfterStart(session, identifier, importHandler,
                "workspace-import");
        JcrNode afterImport = session.getNodeByIdentifier(identifier);

        assertNotSame(cachedDuringImport, afterImport);
    }

    @Test
    public void sessionImportXmlInvalidatesIdentifierCacheOnFailure() throws Exception {
        JcrSession session = login();
        String identifier = createReferenceableNode(session);
        JcrNode beforeImport = session.getNodeByIdentifier(identifier);
        String malformedXml = systemViewNode("partial-import") + "<";

        try {
            session.importXML("/", new ByteArrayInputStream(malformedXml.getBytes(StandardCharsets.UTF_8)),
                    ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            fail("Malformed XML must fail the import");
        } catch (RuntimeException expected) {
            // The cache must also be invalidated when the repository reports an incomplete import.
        }

        JcrNode afterImport = session.getNodeByIdentifier(identifier);
        assertNotSame(beforeImport, afterImport);
    }

    @Test
    public void abortedImportContentHandlerKeepsIdentifierCacheSuspended() throws Exception {
        JcrSession session = login();
        String identifier = createReferenceableNode(session);
        ContentHandler importHandler = session.getImportContentHandler("/", ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
        String malformedXml = systemViewNode("aborted-import") + "<";

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(importHandler);
        try {
            reader.parse(new InputSource(new StringReader(malformedXml)));
            fail("Malformed XML must abort before endDocument");
        } catch (SAXException expected) {
            // Without endDocument the conservative behavior is to bypass the cache for this session wrapper.
        }

        JcrNode firstLookupAfterAbort = session.getNodeByIdentifier(identifier);
        JcrNode secondLookupAfterAbort = session.getNodeByIdentifier(identifier);
        assertNotSame(firstLookupAfterAbort, secondLookupAfterAbort);
    }

    private String createReferenceableNode(JcrSession session) throws RepositoryException {
        JcrNode node = session.getRootNode().addNode("cached-node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();
        return node.getIdentifier();
    }

    private JcrNode importNodeAndCacheAfterStart(JcrSession session, String identifier,
                                                 ContentHandler importHandler, String nodeName) throws Exception {
        JcrNode[] cachedDuringImport = new JcrNode[1];
        XMLFilterImpl forwardingHandler = new XMLFilterImpl() {
            @Override
            public void startDocument() throws SAXException {
                super.startDocument();
                cachedDuringImport[0] = session.getNodeByIdentifier(identifier);
            }
        };
        forwardingHandler.setContentHandler(importHandler);

        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        XMLReader reader = parserFactory.newSAXParser().getXMLReader();
        reader.setContentHandler(forwardingHandler);
        reader.parse(new InputSource(new StringReader(systemViewNode(nodeName))));
        return cachedDuringImport[0];
    }

    private String systemViewNode(String nodeName) {
        return "<sv:node xmlns:sv=\"http://www.jcp.org/jcr/sv/1.0\" sv:name=\"" + nodeName + "\">"
                + "<sv:property sv:name=\"jcr:primaryType\" sv:type=\"Name\">"
                + "<sv:value>nt:unstructured</sv:value>"
                + "</sv:property>"
                + "</sv:node>";
    }

    private JcrSession login() throws RepositoryException {
        Credentials credentials = new SimpleCredentials("admin", "admin".toCharArray());
        JcrSession session = JcrSession.Wrapper.wrap(repo.login(credentials));
        sessions.add(session);
        return session;
    }

    @Test
    public void testgetNodeByUUID() throws RepositoryException {
        JcrSession session = login();

        JcrNode node = session.getRootNode().addNode("node");
        node.addMixin(JcrConstants.MIX_REFERENCEABLE);

        assertNotNull(node.getIdentifier());

        JcrNode node1 = session.getNodeByIdentifier(node.getIdentifier());
        assertNotNull(node1);
        node1.setProperty("property", "value");
    }
}
