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
			String etag = createWeakETag(node, lastModified, contentLength);

			response.setContentType(mimeType);
			response.setHeader("ETag", etag);
			if (lastModified != null) {
				response.setDateHeader("Last-Modified", lastModified.toInstant());
			}

			if (!save && isNotModified(r, lastModified, etag)) {
				response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
				return;
			}

			String fileName = node.getName();
			boolean writeBody = !"HEAD".equalsIgnoreCase(r.getMethod());
			InputStream stream = writeBody ? node.getDataAsStream() : InputStream.nullInputStream();

			new Streamer(contentLength, stream, fileName, save, r, response, writeBody).stream();
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

	private static String createWeakETag(BrixFileNode node, Date lastModified, long contentLength) {
		long lastModifiedTime = lastModified != null ? lastModified.getTime() : -1L;
		return "W/\"" + Integer.toHexString(node.getPath().hashCode()) + "-" +
				Long.toHexString(lastModifiedTime) + "-" + Long.toHexString(contentLength) + "\"";
	}

	private static boolean isNotModified(HttpServletRequest request, Date lastModified, String etag) {
		String ifNoneMatch = request.getHeader("If-None-Match");
		if (!Strings.isEmpty(ifNoneMatch)) {
			return matchesETag(ifNoneMatch, etag);
		}
		return isNotModifiedSince(request, lastModified);
	}

	private static boolean matchesETag(String header, String etag) {
		for (String candidate : header.split(",")) {
			String trimmed = candidate.trim();
			if ("*".equals(trimmed) || etag.equals(trimmed)) {
				return true;
			}
		}
		return false;
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
