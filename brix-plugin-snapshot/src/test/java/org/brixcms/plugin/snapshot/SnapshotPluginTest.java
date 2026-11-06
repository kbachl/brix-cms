package org.brixcms.plugin.snapshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.brixcms.Brix;
import org.brixcms.auth.AuthorizationStrategy;
import org.brixcms.config.BrixConfig;
import org.brixcms.workspace.Workspace;
import org.brixcms.workspace.WorkspaceManager;
import org.junit.Test;

public class SnapshotPluginTest {
    @Test
    public void sortsMixedLegacyDatesNewestFirstForSiteAndSnapshot() {
        Workspace october = snapshot("october", "20. Oktober 2025, 09:53:20 MESZ");
        Workspace earlier = snapshot("earlier", "24. November 2025, 11:27:10 MEZ");
        Workspace later = snapshot("later", "24. November 2025, 11:27:19 MEZ");
        Workspace november = snapshot("november", "November 26, 2025, 11:56:08 PM GMT");
        Workspace nextYear = snapshot("next-year", "2. Januar 2026, 08:00:00 MEZ");
        List<Workspace> original = List.of(earlier, nextYear, october, november, later);
        SnapshotPlugin plugin = plugin(original);
        List<Workspace> expected = List.of(nextYear, november, later, earlier, october);

        assertEquals(expected, plugin.getSnapshotsForWorkspace(site()));
        assertEquals(expected, plugin.getSnapshotsForWorkspace(october));
        assertEquals(expected, plugin.getWorkspaces(site(), true));
        assertEquals("24. November 2025, 11:27:10 MEZ", plugin.getCreated(earlier));
        assertEquals(List.of(earlier, nextYear, october, november, later), original);
    }

    @Test
    public void sortsByInstantAndKeepsMissingOrInvalidDatesAtTheEnd() {
        Workspace german = snapshot("german", "27. November 2025, 00:30:00 MEZ");
        Workspace english = snapshot("english", "November 26, 2025, 11:56:08 PM GMT");
        Workspace equal = snapshot("equal", "26. November 2025, 23:56:08 GMT");
        Workspace missing = snapshot("missing", null);
        Workspace invalid = snapshot("invalid", "31. Februar 2025, 12:00:00 MEZ");
        Workspace trailing = snapshot("trailing", "24. November 2025, 11:27:10 MEZ garbage");
        SnapshotPlugin plugin = plugin(List.of(missing, german, trailing, equal, invalid, english));

        assertEquals(List.of(english, equal, german, invalid, missing, trailing),
                plugin.getSnapshotsForWorkspace(site()));
    }

    @Test
    public void readsDatesWrittenInBundledLocalesAfterServerLocaleChanges() {
        Locale original = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Workspace older = snapshot("older", "1. Januar 2025, 00:00:00 MEZ");
            Workspace newer = snapshot("newer", null);
            SnapshotPlugin plugin = plugin(List.of(older, newer));
            for (Locale locale : List.of(Locale.US, Locale.GERMAN, Locale.forLanguageTag("cs"), Locale.FRANCE)) {
                Locale.setDefault(Locale.Category.FORMAT, locale);
                plugin.setCreated(newer, Date.from(Instant.parse("2025-11-26T23:56:08Z")));
                Locale.setDefault(Locale.Category.FORMAT, Locale.FRANCE);

                assertEquals(locale.toLanguageTag(), List.of(newer, older), plugin.getSnapshotsForWorkspace(site()));
            }
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, original);
        }
    }

    @Test
    public void handlesEmptyListsAndUnrelatedWorkspaces() {
        SnapshotPlugin plugin = plugin(List.of());
        assertTrue(plugin.getSnapshotsForWorkspace(site()).isEmpty());
        assertTrue(plugin.getSnapshotsForWorkspace(new TestWorkspace("unrelated")).isEmpty());
    }

    private static Workspace site() {
        Workspace workspace = new TestWorkspace("site");
        workspace.setAttribute(Brix.WORKSPACE_ATTRIBUTE_TYPE, "brix:site");
        workspace.setAttribute("brix:site-name", "shop");
        return workspace;
    }

    private static Workspace snapshot(String id, String created) {
        Workspace workspace = new TestWorkspace(id);
        workspace.setAttribute(Brix.WORKSPACE_ATTRIBUTE_TYPE, "brix:snapshot");
        workspace.setAttribute("brix:snapshot-site-name", "shop");
        workspace.setAttribute("brix:snapshot-created", created);
        return workspace;
    }

    private static SnapshotPlugin plugin(List<Workspace> snapshots) {
        WorkspaceManager manager = new WorkspaceManager() {
            @Override
            public List<Workspace> getWorkspacesFiltered(Map<String, String> attributes) {
                assertEquals(Map.of(Brix.WORKSPACE_ATTRIBUTE_TYPE, "brix:snapshot",
                        "brix:snapshot-site-name", "shop"), attributes);
                return snapshots;
            }

            @Override
            public Workspace createWorkspace() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Workspace getWorkspace(String id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Workspace> getWorkspaces() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean workspaceExists(String id) {
                throw new UnsupportedOperationException();
            }
        };
        Brix brix = new Brix(new BrixConfig(null, manager, null)) {
            @Override
            public AuthorizationStrategy newAuthorizationStrategy() {
                return null;
            }
        };
        return new SnapshotPlugin(brix);
    }

    private static class TestWorkspace implements Workspace {
        private final String id;
        private final Map<String, String> attributes = new HashMap<>();

        private TestWorkspace(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getAttribute(String key) {
            return attributes.get(key);
        }

        @Override
        public Iterator<String> getAttributeKeys() {
            return attributes.keySet().iterator();
        }

        @Override
        public void setAttribute(String key, String value) {
            attributes.put(key, value);
        }

        @Override
        public void delete() {
            throw new UnsupportedOperationException();
        }
    }
}
