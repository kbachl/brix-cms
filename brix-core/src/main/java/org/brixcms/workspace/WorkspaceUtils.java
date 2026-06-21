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

package org.brixcms.workspace;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.wicket.MetaDataKey;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.http.WebResponse;
import org.apache.wicket.util.string.Strings;
import org.brixcms.Brix;
import org.brixcms.auth.Action;
import org.brixcms.auth.ViewWorkspaceAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkspaceUtils {

    public static final String WORKSPACE_PARAM = Brix.NS_PREFIX + "workspace";

    public static final String COOKIE_NAME = "brix-revision";

    public static final MetaDataKey<String> WORKSPACE_METADATA = new MetaDataKey<String>() {
        private static final long serialVersionUID = 1L;
    };
    private static final Logger log = LoggerFactory.getLogger(WorkspaceUtils.class);

    /**
     * Returns the workspace ID, which is the same as the workspace name.
     * @return workspace ID
     */
    public static String getWorkspaceId() {
        return getWorkspace();
    }

    public static String getWorkspace() {
        RequestCycle rc = RequestCycle.get();
        String workspace = rc.getMetaData(WORKSPACE_METADATA);
        if (workspace != null) {
            return workspace;
        }

        workspace = getWorkspaceFromUrl();
        if (workspace != null && isWorkspaceAccessible(workspace)) {
            return workspace;
        }

        // The URL did not yield a usable workspace (none provided, unknown, or not authorized for the
        // current user): fall back to the remembered/default workspace. The cookie may hold a previously
        // selected workspace, but it is re-validated too, so a workspace remembered from an authenticated
        // preview session cannot leak to an anonymous visitor after logout.
        WebRequest req = (WebRequest) RequestCycle.get().getRequest();
        WebResponse resp = (WebResponse) RequestCycle.get().getResponse();
        Cookie cookie = req.getCookie(COOKIE_NAME);
        workspace = getDefaultWorkspaceName();
        if (cookie != null && cookie.getValue() != null && isWorkspaceAccessible(cookie.getValue())) {
            workspace = cookie.getValue();
        }
        if (!checkSession(workspace)) {
            workspace = getDefaultWorkspaceName();
        }
        if (workspace == null) {
            throw new IllegalStateException("Could not resolve jcr workspace to use for this request");
        }
        Cookie c = new Cookie(COOKIE_NAME, workspace);
        c.setPath("/");
        if (workspace.toString().equals(getDefaultWorkspaceName()) == false) {
            resp.addCookie(c);
        } else if (cookie != null) {
            resp.clearCookie(cookie);
        }
        rc.setMetaData(WORKSPACE_METADATA, workspace);
        return workspace;
    }

    /**
     * Tells whether the current user may use {@code workspaceId} to serve content for this request.
     * <p>
     * A workspace taken from a request URL ({@code ?brix:workspace=} parameter or referer) or from the
     * {@code brix-revision} cookie is only honoured when it exists <em>and</em> the
     * {@link org.brixcms.auth.AuthorizationStrategy} permits viewing it in the presentation context. This
     * prevents an anonymous visitor from forcing a non-default (e.g. draft/staging) workspace by adding
     * a request parameter, which would otherwise serve unpublished content. When access is not permitted
     * the caller falls back to the default workspace.
     * <p>
     * The existence check uses {@link WorkspaceManager#workspaceExists(String)} rather than relying on
     * {@link WorkspaceManager#getWorkspace(String)} returning {@code null}: the RMI client implementation
     * returns a non-null {@code Workspace} even for unknown ids.
     * <p>
     * <strong>Note:</strong> this security gate has no automated unit test because it depends on
     * {@code Brix.get()}, a live {@code WorkspaceManager} and {@code AuthorizationStrategy} and an active
     * {@code RequestCycle}. It must be verified manually or via an integration test (URL parameter denied
     * for unauthorized users, cookie re-validated, unknown workspace falls back to default).
     */
    private static boolean isWorkspaceAccessible(String workspaceId) {
        if (Strings.isEmpty(workspaceId)) {
            return false;
        }
        Brix brix = Brix.get();
        if (!brix.getWorkspaceManager().workspaceExists(workspaceId)) {
            return false;
        }
        Workspace workspace = brix.getWorkspaceManager().getWorkspace(workspaceId);
        return brix.getAuthorizationStrategy()
                .isActionAuthorized(new ViewWorkspaceAction(Action.Context.PRESENTATION, workspace));
    }

    private static String getWorkspaceFromUrl() {
        HttpServletRequest request = (HttpServletRequest) ((WebRequest) RequestCycle.get().getRequest()).getContainerRequest();

        if (request.getParameter(WORKSPACE_PARAM) != null) {
            return request.getParameter(WORKSPACE_PARAM);
        }

        String referer = request.getHeader("referer");

        if (!Strings.isEmpty(referer)) {
            return extractWorkspaceFromReferer(referer);
        } else {
            return null;
        }
    }

    private static String extractWorkspaceFromReferer(String refererURL) {
        int i = refererURL.indexOf('?');
        if (i != -1 && i != refererURL.length() - 1) {
            String param = refererURL.substring(i + 1);
            String params[] = Strings.split(param, '&');
            for (String s : params) {
                try {
                    s = URLDecoder.decode(s, "utf-8");
                } catch (Exception e) {
                    //we just swallow it here since it's just the referer....
                    //log.debug("UnsupportedEncodingException from referer URL", e);
                }
                if (s.startsWith(WORKSPACE_PARAM + "=")) {
                    String value = s.substring(WORKSPACE_PARAM.length() + 1);
                    if (value.length() > 0) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static boolean checkSession(String workspaceId) {
        return Brix.get().getWorkspaceManager().workspaceExists(workspaceId);
    }

    private static String getDefaultWorkspaceName() {
        Brix brix = Brix.get();
        final Workspace workspace = brix.getConfig().getMapper().getWorkspaceForRequest(RequestCycle.get(), brix);
        return (workspace != null) ? workspace.getId() : null;
    }

}
