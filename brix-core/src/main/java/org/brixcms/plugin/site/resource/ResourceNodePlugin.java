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

package org.brixcms.plugin.site.resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.IRequestHandler;
import org.brixcms.Brix;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.plugin.site.NodeConverter;
import org.brixcms.plugin.site.SimpleCallback;
import org.brixcms.plugin.site.SiteNodePlugin;
import org.brixcms.plugin.site.SitePlugin;
import org.brixcms.plugin.site.resource.admin.CreateResourcePanel;
import org.brixcms.plugin.site.resource.admin.ManageResourceNodeTabFactory;
import org.brixcms.plugin.site.resource.managers.image.ImageNodeTabFactory;
import org.brixcms.plugin.site.resource.managers.text.TextNodeTabFactory;
import org.brixcms.web.nodepage.BrixPageParameters;

public class ResourceNodePlugin implements SiteNodePlugin {
    public static final String TYPE = Brix.NS_PREFIX + "resource";

    private Map<String /* extension */, String /* mime-type */> mimeTypes = new ConcurrentHashMap<String, String>();

    public ResourceNodePlugin(SitePlugin sp) {
        registerDefaultMimeTypes();
        sp.registerManageNodeTabFactory(new ManageResourceNodeTabFactory());
        sp.registerManageNodeTabFactory(new ImageNodeTabFactory());
        sp.registerManageNodeTabFactory(new TextNodeTabFactory());
    }

    private void registerDefaultMimeTypes() {
        registerMimeType("application/xml", "xml");
        registerMimeType("text/html", "html", "htm", "dwt");
        registerMimeType("text/plain", "txt");
        registerMimeType("text/css", "css");
        registerMimeType("text/javascript", "js");
        registerMimeType("image/jpeg", "jpg", "jpeg");
        registerMimeType("image/png", "png");
        registerMimeType("image/gif", "gif");
        registerMimeType("application/octet-stream", "exe");
        registerMimeType("application/octet-stream", "dmg");
    }

    public void registerMimeType(String mimeType, String... extensions) {
        for (String s : extensions) {
            mimeTypes.put(s, mimeType);
        }
    }

    /**
     * Normalizes a raw MIME type for <em>storage</em> as {@code jcr:mimeType}: returns the base type
     * (lowercased, parameters stripped), migrating legacy JavaScript types to the modern
     * {@code text/javascript} (RFC 9239). Use this when persisting a MIME type so the stored value is
     * clean regardless of what a browser supplied on upload (browsers still send
     * {@code application/x-javascript} for {@code .js} files). Returns {@code null} for {@code null}.
     */
    public static String normalizeMimeType(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        String base = baseType(mimeType);
        return isLegacyJavaScript(base) ? "text/javascript" : base;
    }

    /**
     * The base type of a Content-Type value: the substring before the first {@code ;}, trimmed and
     * lowercased.
     */
    public static String baseType(String mimeType) {
        int semi = mimeType.indexOf(';');
        return (semi == -1 ? mimeType : mimeType.substring(0, semi)).trim().toLowerCase();
    }

    /**
     * Legacy JavaScript MIME types (RFC 9239 / WHATWG MIME Sniffing "JavaScript MIME type" group) that
     * should be stored/served as the modern {@code text/javascript}. The canonical
     * {@code text/javascript} itself is excluded because it needs no migration.
     */
    public static boolean isLegacyJavaScript(String baseType) {
        if ("text/javascript".equals(baseType)) {
            return false;
        }
        // Versioned forms live only under the text tree: RFC 9239 enumerates text/javascript1.0..1.5;
        // some servers also emit higher/suffixed values, which are matched tolerantly here.
        if (baseType.startsWith("text/javascript1")) {
            return true;
        }
        switch (baseType) {
            case "application/javascript":
            case "application/x-javascript":
            case "application/ecmascript":
            case "application/x-ecmascript":
            case "text/ecmascript":
            case "text/x-ecmascript":
            case "text/x-javascript":
            case "text/jscript":
            case "text/livescript":
                return true;
            default:
                return false;
        }
    }


    public String getNodeType() {
        return TYPE;
    }

    public String getName() {
        return (new ResourceModel("resource", "Resource")).getObject();
    }

    public IRequestHandler respond(IModel<BrixNode> nodeModel, BrixPageParameters requestParameters) {
        // IRequestTarget switchTarget =
        // SwitchProtocolRequestTarget.requireProtocol(Protocol.HTTP);
        // if (switchTarget != null)
        // {
        // return switchTarget;
        // }
        // else
        // {
		return new ResourceNodeHandler(nodeModel);
        // }
    }

    public IModel<String> newCreateNodeCaptionModel(IModel<BrixNode> parentNode) {
        return new ResourceModel("create");
    }

    public Panel newCreateNodePanel(String id, IModel<BrixNode> parentNode, SimpleCallback goBack) {
        return new CreateResourcePanel(id, parentNode, goBack);
    }

    public NodeConverter getConverterForNode(BrixNode node) {
        return null;
    }

    public String resolveMimeTypeFromFileName(String fileName) {
        int last = fileName.lastIndexOf(".");
        if (last != -1) {
            String ext = fileName.substring(last + 1).toLowerCase();
            return mimeTypeFromExtension(ext);
        }
        return null;
    }

    private String mimeTypeFromExtension(String ext) {
        return mimeTypes.get(ext);
    }
}
