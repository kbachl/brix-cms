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

package org.brixcms.jcr.wrapper;

import org.apache.wicket.util.io.Streams;
import org.apache.wicket.util.string.Strings;
import org.brixcms.Brix;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.plugin.site.SitePlugin;
import org.brixcms.plugin.site.resource.ResourceNodePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.HexFormat;

/**
 * Base class for nodes with content (with JCR primary type nt:file).
 *
 * @author Matej Knopp
 * @see #initialize(JcrNode, String)
 */
public class BrixFileNode extends BrixNode {
    private static final Logger log = LoggerFactory.getLogger(BrixFileNode.class);
    private static final String JCR_PROP_CONTENT_SHA256 = Brix.NS_PREFIX + "contentSha256";
    private static final String JCR_PROP_CONTENT_SHA256_LENGTH = Brix.NS_PREFIX + "contentSha256Length";
    private static final String JCR_PROP_CONTENT_SHA256_LAST_MODIFIED =
            Brix.NS_PREFIX + "contentSha256LastModified";

    /**
     * Returns if the node is a file node,
     *
     * @param node
     * @return <code>true</code> if the node is a file node, <code>false</code> otherwise
     */
    public static boolean isFileNode(JcrNode node) {
        if (!node.getPrimaryNodeType().getName().equals("nt:file")) {
            return false;
        }

        return node.hasNode("jcr:content");
    }

    /**
     * Initializes the specified node to be a valid file node. The node's primary type must be nt:file.
     *
     * @param node
     * @param mimeType
     * @return
     */
    public static BrixFileNode initialize(JcrNode node, String mimeType) {
        if (node.isNodeType("nt:file") == false) {
            throw new IllegalStateException("Argument 'node' must have JCR type nt:file.");
        } else if (node instanceof BrixFileNode fileNode) {
            return fileNode;
        }
        node.addNode("jcr:content", "nt:resource");
        BrixFileNode wrapped = new BrixFileNode(node.getDelegate(), node.getSession());
        // Normalize the stored MIME type (e.g. browsers upload .js as application/x-javascript);
        // legacy JavaScript types become text/javascript (RFC 9239).
        wrapped.setMimeType(ResourceNodePlugin.normalizeMimeType(mimeType));
        wrapped.getContent().setProperty("jcr:lastModified", Calendar.getInstance());
        wrapped.getContent().setProperty("jcr:data", "");
        return wrapped;
    }

    /**
     * Sets the mime type property
     *
     * @param mimeType
     */
    public void setMimeType(String mimeType) {
        getContent().setProperty("jcr:mimeType", mimeType);
    }

    public static boolean isText(String mimeType) {
        if (Strings.isEmpty(mimeType)) {
            return false;
        }
        if (mimeType.equals("text") || mimeType.startsWith("text/")) {
            return true;
        }
        if ("application/xml".equals(mimeType)) {
            return true;
        }
        return false;
    }

    public static boolean isText(BrixFileNode node) {
        if (node == null) {
            throw new IllegalArgumentException("Argument 'node' cannot be null");
        }
        return isText(node.getMimeType());
    }

    /**
     * Returns the mime type property. If the property is not specified, tries to determine mime type from node name
     * extension.
     *
     * @return
     */
    public String getMimeType() {
        return getMimeType(true);
    }

    /**
     * Wraps the given delegate node using provided {@link JcrSession}.
     *
     * @param delegate
     * @param session
     */
    public BrixFileNode(Node delegate, JcrSession session) {
        super(delegate, session);
    }

    private JcrNode getContent() {
        return (JcrNode) getPrimaryItem();
    }

    /**
     * Returns the length of content in bytes
     *
     * @return
     */
    public long getContentLength() {
        return getContent().getProperty("jcr:data").getLength();
    }

    public String getContentSha256() {
        if (hasProperty(JCR_PROP_CONTENT_SHA256)) {
            String hash = getProperty(JCR_PROP_CONTENT_SHA256).getString();
            if (!Strings.isEmpty(hash)) {
                return hash;
            }
        }
        return null;
    }

    /**
     * Returns the persisted SHA-256 of the current content if one is already stored and still matches
     * the current content length and modification revision, or {@code null} otherwise.
     * <p>
     * Unlike {@link #ensureContentSha256()} this never reads or hashes the binary and never persists
     * anything: it only reads the hash metadata and compares it against the current resource metadata. It
     * is therefore cheap to call on every request - including conditional (304) and HEAD requests that
     * will never stream a body - so the ETag/conditional decision can be made without touching the binary.
     * When no up-to-date persisted hash exists yet the caller gets {@code null} and can decide whether
     * hashing is warranted (e.g. only when actually sending a body).
     */
    public String getCachedContentSha256() {
        String hash = getContentSha256();
        if (hash != null && isPersistedHashForCurrentContent()) {
            return hash;
        }
        return null;
    }

    public String ensureContentSha256() {
        String hash = getContentSha256();
        if (hash != null && isPersistedHashForCurrentContent()) {
            return hash;
        }
        if (getContentLastModified() == null) {
            touchContentLastModified();
        }
        long contentLength = getContentLength();
        hash = calculateContentSha256();
        setContentSha256(hash, contentLength);
        saveHashBestEffort();
        return hash;
    }

    /**
     * Tells whether the persisted SHA-256 was computed for the content currently stored on this node.
     * <p>
     * Content can be replaced through paths that bypass {@link #setData} (for example an XML workspace
     * import), which would otherwise leave a stale hash and therefore a stale ETag. Re-validating both the
     * content length and the resource's {@code jcr:lastModified} revision detects same-length replacements
     * without having to re-hash the binary on every request. Legacy hashes without complete validation
     * metadata are treated as stale and backfilled on the next body-serving request.
     */
    private boolean isPersistedHashForCurrentContent() {
        try {
            if (!hasProperty(JCR_PROP_CONTENT_SHA256_LENGTH)
                    || !hasProperty(JCR_PROP_CONTENT_SHA256_LAST_MODIFIED)) {
                return false;
            }
            Long contentLastModified = getContentLastModified();
            return contentLastModified != null
                    && getProperty(JCR_PROP_CONTENT_SHA256_LENGTH).getLong() == getContentLength()
                    && getProperty(JCR_PROP_CONTENT_SHA256_LAST_MODIFIED).getLong()
                    == contentLastModified.longValue();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String calculateContentSha256() {
        try (InputStream data = getDataAsStream()) {
            return sha256Hex(data);
        } catch (IOException e) {
            throw new RuntimeException("Unable to calculate content hash for " + getPath(), e);
        }
    }

    private void updateContentSha256() {
        setContentSha256(calculateContentSha256(), getContentLength());
    }

    private void setContentSha256(String hash, long contentLength) {
        if (!Strings.isEmpty(hash)) {
            if (!isNodeType(JCR_TYPE_BRIX_NODE)) {
                addMixin(JCR_TYPE_BRIX_NODE);
            }
            setProperty(JCR_PROP_CONTENT_SHA256, hash);
            setProperty(JCR_PROP_CONTENT_SHA256_LENGTH, contentLength);
            Long contentLastModified = getContentLastModified();
            if (contentLastModified != null) {
                setProperty(JCR_PROP_CONTENT_SHA256_LAST_MODIFIED, contentLastModified.longValue());
            }
        }
    }

    private Long getContentLastModified() {
        JcrNode content = getContent();
        if (!content.hasProperty("jcr:lastModified")) {
            return null;
        }
        return content.getProperty("jcr:lastModified").getDate().getTimeInMillis();
    }

    /**
     * Advances the revision used to validate the persisted content hash. The timestamp is guaranteed to
     * increase even when two changes happen within the same millisecond.
     */
    public void touchContentLastModified() {
        long timestamp = System.currentTimeMillis();
        Long previous = getContentLastModified();
        if (previous != null && timestamp <= previous.longValue()) {
            timestamp = previous.longValue() + 1;
        }
        Calendar lastModified = Calendar.getInstance();
        lastModified.setTimeInMillis(timestamp);
        getContent().setProperty("jcr:lastModified", lastModified);
    }

    private void saveHashBestEffort() {
        try {
            save();
        } catch (RuntimeException e) {
            // The computed hash is still valid for this response; persistence only avoids re-hashing the
            // binary on subsequent requests. Log instead of swallowing silently so a persistently failing
            // backfill (and the resulting per-request re-hash) stays observable.
            log.debug("Could not persist content SHA-256 for {} (hash remains valid for this response)",
                    getPath(), e);
        }
    }

    /**
     * Returns the data of this node as string
     *
     * @return
     */
    public String getDataAsString() {
        return getContent().getProperty("jcr:data").getString();
    }

    /**
     * Returns the encoding property
     *
     * @return
     */
    public String getEncoding() {
        return getContent().hasProperty("jcr:encoding") ? getContent().getProperty("jcr:encoding")
                .getString() : null;
    }

    /**
     * Returns the mime type for this node. If the property is not specified and <code>useExtension</code> is
     * <code>true</code>, tries to determine mime type from extension.
     *
     * @param useExtension
     * @return
     */
    public String getMimeType(boolean useExtension) {
        // FIXME Shouldn't have direct dependency on SitePlugin

        String mime = getContent().getProperty("jcr:mimeType").getString();
        if (useExtension && (Strings.isEmpty(mime) || mime.equals("application/octet-stream"))) {
            ResourceNodePlugin plugin = (ResourceNodePlugin) SitePlugin.get(getBrix())
                    .getNodePluginForType(ResourceNodePlugin.TYPE);
            return plugin.resolveMimeTypeFromFileName(getName());
        }
        return mime;
    }

    /**
     * Sets the actual data of this node
     *
     * @param data
     */
    public void setData(Binary data) {
        getContent().setProperty("jcr:data", data);
        touchContentLastModified();
        updateContentSha256();
    }

    /**
     * Sets the actual data of this node. Provided as complementary setter for {@link #getDataAsString()}.
     *
     * @param data
     */
    public void setDataAsString(String data) {
        setData(data);
    }

    /**
     * Sets the actual data of this node
     *
     * @param data
     */
    public void setData(String data) {
        if (data == null) {
            data = "";
        }
        setEncoding("UTF-8");
        getContent().setProperty("jcr:data", data);
        touchContentLastModified();
        updateContentSha256();
    }

    /**
     * Sets the encoding property
     *
     * @param encoding
     */
    public void setEncoding(String encoding) {
        getContent().setProperty("jcr:encoding", encoding);
    }

    /**
     * Writes the node data to the specified output stream.
     *
     * @param stream
     * @throws IOException
     */
    public void writeData(OutputStream stream) throws IOException {
        try (InputStream data = getDataAsStream()) {
            Streams.copy(data, stream);
        }
    }

    /**
     * Returns the data of this node as stream
     *
     * @return
     */
    public InputStream getDataAsStream() {
        try {
            Binary binary = getContent().getProperty("jcr:data").getBinary();
            return new FilterInputStream(binary.getStream()) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        binary.dispose();
                    }
                }
            };
        }
        catch (RepositoryException e) {
            throw new RuntimeException(e);
        }
    }

    static String sha256Hex(InputStream stream) throws IOException {
        MessageDigest digest = sha256Digest();
        try (DigestInputStream digestStream = new DigestInputStream(stream, digest)) {
            Streams.copy(digestStream, OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
