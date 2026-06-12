package org.brixcms.jcr.wrapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import org.brixcms.jcr.RepositoryUtil;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
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
