package org.brixcms.jcr.wrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.Workspace;

import org.apache.jackrabbit.api.JackrabbitRepository;
import org.apache.jackrabbit.core.RepositoryImpl;
import org.apache.jackrabbit.core.config.RepositoryConfig;
import org.brixcms.Brix;
import org.brixcms.jcr.JcrEventListener;
import org.brixcms.jcr.RepositoryUtil;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.base.EventUtil;
import org.junit.After;
import org.junit.Test;

public class BrixFileNodeTest {
    private Repository repo;
    private List<JcrSession> sessions;
    private File home;

    private void setupRepository() throws IOException, RepositoryException {
        String temp = System.getProperty("java.io.tmpdir");
        home = new File(temp, getClass().getName());
        delete(home);
        home.deleteOnExit();

        if (!home.mkdirs()) {
            throw new RuntimeException("Could not create directory: " + home.getAbsolutePath());
        }

        try (InputStream configStream = getClass().getResourceAsStream("repository.xml")) {
            if (configStream == null) {
                throw new IllegalStateException("Missing test repository.xml");
            }
            RepositoryConfig config = RepositoryConfig.create(configStream, home.getAbsolutePath());
            repo = RepositoryImpl.create(config);
        }
        sessions = new ArrayList<JcrSession>();

        Session rawSession = repo.login(credentials());
        try {
            registerBrixNodeTypes(rawSession.getWorkspace());
            rawSession.save();
        } finally {
            rawSession.logout();
        }
    }

    @After
    public void cleanupRepository() {
        if (sessions != null) {
            for (JcrSession session : sessions) {
                if (session.isLive()) {
                    session.logout();
                }
            }
        }
        if (repo != null) {
            ((JackrabbitRepository) repo).shutdown();
        }
        if (home != null) {
            delete(home);
        }
    }

    @Test
    public void sha256HexHashesStreamContent() throws Exception {
        String hash = BrixFileNode.sha256Hex(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)));

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
    }

    @Test
    public void setDataAddsBrixNodeMixinBeforePersistingContentHash() throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        assertFalse(node.isNodeType(BrixNode.JCR_TYPE_BRIX_NODE));

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();

        assertTrue(file.isNodeType(BrixNode.JCR_TYPE_BRIX_NODE));
        assertEquals("230d8358dc8e8890b4c58deeb62912ee2f20357ae92a5cc861b98e68fe31acb5",
                file.getContentSha256());
    }

    @Test
    public void staleHashIsRecomputedWhenContentChangesOutsideSetData() throws IOException, RepositoryException {
        // Simulates a content change that bypasses BrixFileNode.setData() (e.g. an XML workspace import):
        // the persisted hash would otherwise stay stale and serve an incorrect ETag.
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();

        String stale = file.getContentSha256();

        // External write directly to jcr:content/jcr:data, no hash update.
        file.getNode("jcr:content").setProperty("jcr:data", "a completely different body");
        root.save();

        String recomputed = file.ensureContentSha256();

        assertFalse("hash must change when content changes", stale.equals(recomputed));
        assertEquals("hash must reflect the current content", file.calculateContentSha256(), recomputed);
        // After recompute the persisted length matches, so the property now holds the fresh value.
        assertEquals(recomputed, file.getContentSha256());
    }

    @Test
    public void staleHashIsRejectedAfterSameLengthContentChange() throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();

        String stale = file.getContentSha256();

        // Simulate a direct JCR write that bypasses BrixFileNode.setData(). The replacement deliberately
        // has the same byte length, so length-only validation would accept the stale hash.
        file.getNode("jcr:content").setProperty("jcr:data", "copy");
        JcrEventListener listener = new JcrEventListener();
        EventUtil.registerSaveEventListener(listener);
        try {
            EventUtil.raiseSaveEvent(root);
            root.save();
        } finally {
            EventUtil.unregisterSaveEventListener(listener);
        }

        assertNull(file.getCachedContentSha256());
        String recomputed = file.ensureContentSha256();

        assertFalse("hash must change for a same-length replacement", stale.equals(recomputed));
        assertEquals(file.calculateContentSha256(), recomputed);
    }

    @Test
    public void legacyHashWithoutModificationRevisionIsRecomputed() throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();
        file.getProperty(Brix.NS_PREFIX + "contentSha256LastModified").remove();
        root.save();

        assertNull(file.getCachedContentSha256());
        String recomputed = file.ensureContentSha256();

        assertEquals(file.calculateContentSha256(), recomputed);
        assertEquals(recomputed, file.getCachedContentSha256());
    }

    @Test
    public void getCachedContentSha256ReturnsThePersistedHash() throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();

        String expected = file.calculateContentSha256();
        // The persisted hash is up to date, so the cheap accessor returns it verbatim.
        assertEquals(expected, file.getCachedContentSha256());
    }

    @Test
    public void getCachedContentSha256IsNullAndPersistsNothingWhenNoHashExists()
            throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");
        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        root.save();

        // No hash has been persisted yet. The cheap accessor must return null and must NOT compute,
        // persist, or add the brix mixin - so it stays safe on the 304/HEAD hot path.
        assertNull(file.getCachedContentSha256());
        assertFalse(file.hasProperty(Brix.NS_PREFIX + "contentSha256"));
        assertFalse(file.isNodeType(BrixNode.JCR_TYPE_BRIX_NODE));
    }

    @Test
    public void getCachedContentSha256IsNullAfterExternalContentChangeButEnsureRecomputes()
            throws IOException, RepositoryException {
        setupRepository();

        JcrSession session = login();
        JcrNode root = session.getRootNode().addNode("root", "nt:folder");
        JcrNode node = root.addNode("asset.css", "nt:file");

        BrixFileNode file = BrixFileNode.initialize(node, "text/css");
        file.setData("body");
        root.save();

        // Replace content bypassing setData (e.g. an XML workspace import): the stored length no longer
        // matches, so the persisted hash is considered stale and the cheap accessor returns null.
        file.getNode("jcr:content").setProperty("jcr:data", "a completely different body");
        root.save();

        assertNull(file.getCachedContentSha256());
        String recomputed = file.ensureContentSha256();
        assertEquals(file.calculateContentSha256(), recomputed);
    }

    private static void registerBrixNodeTypes(Workspace workspace) throws RepositoryException {
        try {
            workspace.getNamespaceRegistry().registerNamespace(Brix.NS, "http://brix-cms.googlecode.com");
        } catch (RepositoryException ignore) {
        }
        RepositoryUtil.registerBrixUnstructuredMixin(workspace);
        RepositoryUtil.registerNodeType(workspace, BrixNode.JCR_TYPE_BRIX_NODE, true, true, true);
    }

    private JcrSession login() throws RepositoryException {
        JcrSession session = JcrSession.Wrapper.wrap(repo.login(credentials()));
        sessions.add(session);
        return session;
    }

    private static Credentials credentials() {
        return new SimpleCredentials("admin", "admin".toCharArray());
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
}
