# Audit TODO

## P1 - Make publishing and restore atomic or recoverable

Both publishing and snapshot restore remove the target workspace content before cloning or importing replacement content. A failed clone, malformed snapshot, or process interruption can leave the published or restored workspace empty or partially rebuilt, affecting all end users of that workspace.

- Stage the replacement in an isolated workspace, then switch it atomically where possible.
- Otherwise retain a rollback copy and restore it on failure.
- Coordinate concurrent publish and restore operations.
- Test failed clone and failed import paths, including preservation of the prior public content.

Relevant code: `brix-core/src/main/java/org/brixcms/Brix.java`, `brix-plugin-publish/src/main/java/org/brixcms/plugin/publish/PublishingPlugin.java`, and `brix-plugin-snapshot/src/main/java/org/brixcms/plugin/snapshot/web/ManageSnapshotsPanel.java`.

## P2 - Remove temporary files created during resource uploads

Resource upload creates a temporary file for each upload and does not delete it after storing the binary in JCR. Repeated uploads can fill the server temporary directory and eventually cause user-visible upload failures. The involved streams should also be closed deterministically.

- Use try-with-resources for the upload and temporary-file streams.
- Delete the temporary file in a finally block, including failures.
- Add a regression test or integration-level cleanup check.

Relevant code: `brix-core/src/main/java/org/brixcms/web/genericpage/UploadResourcesPanel.java`.

## P2 - Prevent stale ETags after same-length resource replacements

The file hash cache trusts a cached digest when the binary length is unchanged. External JCR or XML updates that replace a resource with different bytes of the same length can retain the old ETag. Browsers and intermediaries may then receive a false `304 Not Modified` response and continue showing an old asset.

- Invalidate the cached hash on all mutation and import paths, including external changes, or use a repository revision/version signal rather than length alone.
- Cover same-length replacement and legacy resource metadata in tests.

Relevant code: `brix-core/src/main/java/org/brixcms/web/nodepage/BrixFileNode.java` and `brix-core/src/main/java/org/brixcms/web/resource/ResourceNodeHandler.java`.

## P2 - Preserve the character encoding of uploaded text resources

Uploaded `text/*` resources are stored without an encoding property, while delivery adds UTF-8 as the default charset. A non-UTF-8 upload can therefore be declared as UTF-8 and render incorrectly in a browser.

- Store an explicit encoding when it is known, especially for editor-created text.
- Only append a charset when the encoding is reliable, or define a clear conversion policy at upload time.
- Add coverage for a non-UTF-8 text resource.

Relevant code: `brix-core/src/main/java/org/brixcms/web/genericpage/UploadResourcesPanel.java`, `brix-core/src/main/java/org/brixcms/web/nodepage/BrixFileNode.java`, and `brix-core/src/main/java/org/brixcms/web/resource/ResourceNodeHandler.java`.
