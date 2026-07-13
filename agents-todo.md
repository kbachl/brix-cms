# Audit TODO

## P1 - Make publishing and restore atomic or recoverable

Both publishing and snapshot restore remove the target workspace content before cloning or importing replacement content. A failed clone, malformed snapshot, or process interruption can leave the published or restored workspace empty or partially rebuilt, affecting all end users of that workspace.

- Stage the replacement in an isolated workspace, then switch it atomically where possible.
- Otherwise retain a rollback copy and restore it on failure.
- Coordinate concurrent publish and restore operations.
- Test failed clone and failed import paths, including preservation of the prior public content.

Relevant code: `brix-core/src/main/java/org/brixcms/Brix.java`, `brix-plugin-publish/src/main/java/org/brixcms/plugin/publish/PublishingPlugin.java`, and `brix-plugin-snapshot/src/main/java/org/brixcms/plugin/snapshot/web/ManageSnapshotsPanel.java`.
