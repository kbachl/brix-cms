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
package org.brixcms.plugin.site.resource;

import java.io.InputStream;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.wicket.model.IModel;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.util.string.Strings;
import org.brixcms.Brix;
import org.brixcms.auth.Action;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.wrapper.BrixFileNode;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.plugin.site.SitePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResourceNodeHandler implements IRequestHandler {
	// ------------------------------ FIELDS ------------------------------

	public static final String SAVE_PARAMETER = Brix.NS_PREFIX + "save";

	private static final Logger log = LoggerFactory.getLogger(ResourceNodeHandler.class);
	private final IModel<BrixNode> node;
	private final Boolean save;

	// --------------------------- CONSTRUCTORS ---------------------------

	public ResourceNodeHandler(IModel<BrixNode> node) {
		super();
		this.node = node;
		this.save = null;
	}

	public ResourceNodeHandler(IModel<BrixNode> node, boolean save) {
		super();
		this.node = node;
		this.save = save;
	}

	// ------------------------ INTERFACE METHODS ------------------------

	// --------------------- Interface IRequestTarget ---------------------

	@Override
	public void respond(IRequestCycle requestCycle) {
		boolean save = (this.save != null) ? this.save : Strings.isTrue(requestCycle.getRequest().getRequestParameters()
				.getParameterValue(SAVE_PARAMETER).toString());

		BrixFileNode node = (BrixFileNode) this.node.getObject();
		if (!SitePlugin.get().canViewNode(node, Action.Context.PRESENTATION)) {
			throw Brix.get().getForbiddenException();
		}

		WebResponse response = (WebResponse) requestCycle.getResponse();

		try {
			final HttpServletRequest r = (HttpServletRequest) requestCycle.getRequest().getContainerRequest();
			String mimeType = resolveMimeType(node);
			Date lastModified = resolveLastModified(node);
			long contentLength = node.getContentLength();
			if (contentLength < 0) {
				throw new IllegalStateException("Resource content length is negative for " + node.getPath());
			}

			// Use the already-persisted hash (if any) for the ETag and the conditional decision. This is
			// cheap: it only reads the two hash properties and compares the stored length against the
			// current content length - it never reads or hashes the binary and never persists anything.
			// When no up-to-date persisted hash exists yet, the ETag stays null and the conditional
			// decision falls back to Last-Modified (and to "not modified == false" for If-None-Match,
			// which is correct: without a server ETag there is nothing to match against).
			String cachedHash = resolveCachedContentHash(node);
			String etag = createContentETag(cachedHash);

			response.setContentType(mimeType);
			if (etag != null) {
				response.setHeader("ETag", etag);
			}
			if (lastModified != null) {
				response.setDateHeader("Last-Modified", lastModified.toInstant());
			}

			if (!save && isNotModified(r, lastModified, etag)) {
				response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}

			// We are about to send a representation. Only now is it worth ensuring a correct, persisted
			// hash: on the first request after a content change this backfills and persists the hash (the
			// write-on-read behaviour is intentionally retained); on subsequent requests it is a cheap
			// cache hit. 304 and HEAD never reach this point, so they never hash or save.
			boolean writeBody = !"HEAD".equalsIgnoreCase(r.getMethod());
			if (cachedHash == null && writeBody) {
				String ensuredEtag = createContentETag(resolveContentHash(node));
				if (ensuredEtag != null) {
					etag = ensuredEtag;
					response.setHeader("ETag", etag);
				}
			}

			// RFC 7233 If-Range: a client resuming a download sends the validator of the representation
			// it already has. Only honour the Range when that validator still matches the current entity;
			// otherwise serve the full representation so the client does not splice bytes of a new version
			// into a partial download of the old one.
			boolean allowRange = ifRangeMatches(r, etag, lastModified);

			String fileName = node.getName();
			InputStream stream = writeBody ? node.getDataAsStream() : InputStream.nullInputStream();

			new Streamer(contentLength, stream, fileName, save, r, response, writeBody, allowRange).stream();
		} catch (Exception e) {
			if (isClientAbort(e)) {
				log.debug("Client aborted while streaming resource (ignored): {}", rootMessage(e));
			} else {
				log.error("Error writing resource data to content", e);
				try {
					response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error writing resource data");
				} catch (Exception sendErrorException) {
					log.debug("Unable to send resource error response", sendErrorException);
				}
			}
		}
	}

	private static String resolveMimeType(BrixFileNode node) {
		String mimeType = null;
		try {
			mimeType = node.getMimeType();
		} catch (Exception e) {
			log.debug("Unable to read resource MIME type, falling back to file extension for {}", node.getPath(), e);
		}
		if (Strings.isEmpty(mimeType)) {
			ResourceNodePlugin plugin = (ResourceNodePlugin) SitePlugin.get(node.getBrix())
					.getNodePluginForType(ResourceNodePlugin.TYPE);
			mimeType = plugin.resolveMimeTypeFromFileName(node.getName());
		}
		return Strings.isEmpty(mimeType) ? "application/octet-stream" : mimeType;
	}

	private static Date resolveLastModified(BrixFileNode node) {
		Date lastModified = node.getLastModified();
		if (lastModified != null) {
			return lastModified;
		}
		if (node.hasNode("jcr:content")) {
			try {
				JcrNode content = node.getNode("jcr:content");
				if (content.hasProperty("jcr:lastModified")) {
					return content.getProperty("jcr:lastModified").getDate().getTime();
				}
			} catch (Exception e) {
				log.debug("Unable to read jcr:lastModified fallback for {}", node.getPath(), e);
			}
		}
		return null;
	}

	private static String resolveContentHash(BrixFileNode node) {
		try {
			return node.ensureContentSha256();
		} catch (RuntimeException e) {
			log.warn("Unable to calculate resource content hash for {}", node.getPath(), e);
			return null;
		}
	}

	/**
	 * Reads the already-persisted hash for the conditional decision without ever hashing the binary.
	 * Mirrors {@link #resolveContentHash}: a transient JCR read failure must degrade gracefully to a
	 * null ETag (so the request still succeeds) rather than escaping to the outer handler and turning
	 * into an HTTP 500 - especially on the 304/HEAD path that does not even need the hash.
	 */
	private static String resolveCachedContentHash(BrixFileNode node) {
		try {
			return node.getCachedContentSha256();
		} catch (RuntimeException e) {
			log.warn("Unable to read cached content hash for {}", node.getPath(), e);
			return null;
		}
	}

	static String createContentETag(String contentSha256) {
		if (Strings.isEmpty(contentSha256)) {
			return null;
		}
		return "W/\"sha256-" + contentSha256 + "\"";
	}

	static boolean isNotModified(HttpServletRequest request, Date lastModified, String etag) {
		// Per RFC 7232 the If-None-Match validator takes precedence. Only when the client did not
		// send If-None-Match do we fall back to If-Modified-Since - the presence of a server-side
		// ETag must not disable date-based conditional requests for clients that rely on them.
		String ifNoneMatch = request.getHeader("If-None-Match");
		if (!Strings.isEmpty(ifNoneMatch)) {
			return matchesETag(ifNoneMatch, etag);
		}
		return isNotModifiedSince(request, lastModified);
	}

	static boolean matchesETag(String header, String etag) {
		for (String candidate : header.split(",")) {
			String trimmed = candidate.trim();
			// The wildcard matches resource existence (RFC 7232), not a specific server ETag, so it is
			// evaluated even when we do not (yet) have a persisted hash and therefore no ETag to compare
			// against. Without this, an uncached resource answering "If-None-Match: *" would wrongly get
			// a 200 instead of 304.
			if ("*".equals(trimmed)) {
				return true;
			}
			if (!Strings.isEmpty(etag)
					&& (etag.equals(trimmed) || stripWeakPrefix(etag).equals(stripWeakPrefix(trimmed)))) {
				return true;
			}
		}
		return false;
	}

	private static String stripWeakPrefix(String etag) {
		return etag != null && etag.startsWith("W/") ? etag.substring(2) : etag;
	}

	private static boolean isNotModifiedSince(HttpServletRequest request, Date lastModified) {
		if (lastModified == null || request.getHeader("If-Modified-Since") == null) {
			return false;
		}
		try {
			long dateHeader = request.getDateHeader("If-Modified-Since");
			return dateHeader != -1 && lastModified.getTime() / 1000 <= dateHeader / 1000;
		} catch (IllegalArgumentException ignored) {
			return false;
		}
	}

	/**
	 * Evaluates an {@code If-Range} precondition (RFC 7233) against the current entity and reports
	 * whether a Range request may be honoured.
	 * <p>
	 * Returns {@code true} (range allowed) when there is no {@code If-Range} header, or when its
	 * validator still matches the current representation. Returns {@code false} when {@code If-Range}
	 * is present but does not match, which makes the caller serve the full representation instead of a
	 * partial one - preventing a resuming client from splicing bytes of a new version into a partial
	 * download of the old one.
	 * <p>
	 * The validator is either an HTTP-date or an entity-tag. The entity-tag comparison reuses
	 * {@link #matchesETag} (weak/strong tolerant); this is safe here because the ETag is a content hash,
	 * so a match guarantees byte-identical content.
	 */
	static boolean ifRangeMatches(HttpServletRequest request, String etag, Date lastModified) {
		String ifRange = request.getHeader("If-Range");
		if (Strings.isEmpty(ifRange)) {
			return true;
		}
		long ifRangeDate;
		try {
			ifRangeDate = request.getDateHeader("If-Range");
		} catch (IllegalArgumentException notADate) {
			ifRangeDate = -1;
		}
		if (ifRangeDate != -1) {
			// HTTP-date form: the range is valid only if the entity has not changed since that date.
			if (lastModified == null) {
				return false;
			}
			return lastModified.getTime() / 1000 <= ifRangeDate / 1000;
		}
		// Entity-tag form.
		return matchesETag(ifRange, etag);
	}

	private static boolean isClientAbort(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			String m = t.getMessage();
			if (m == null) continue;
			m = m.toLowerCase();
			if (m.contains("broken pipe") ||
					m.contains("connection reset") ||
					m.contains("reset by peer") ||
					m.contains("connection is closed") ||
					m.contains("stream was already closed") ||
					m.contains("h2exception")) {
				return true;
			}
		}
		return false;
	}

	private static String rootMessage(Throwable t) {
		Throwable c = t;
		while (c.getCause() != null) c = c.getCause();
		return c.getMessage();
	}


	@Override
	public void detach(IRequestCycle requestCycle) {
		node.detach();
	}
}
