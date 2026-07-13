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

package org.brixcms.plugin.site.resource.admin;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;

import javax.jcr.Binary;

import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.MultiFileUploadField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.brixcms.jcr.api.JcrValueFactory;
import org.brixcms.jcr.wrapper.BrixFileNode;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.plugin.site.SimpleCallback;
import org.brixcms.plugin.site.SitePlugin;
import org.brixcms.plugin.site.admin.NodeManagerPanel;
import org.brixcms.plugin.site.resource.ResourceNodePlugin;
import org.brixcms.web.ContainerFeedbackPanel;

public class UploadResourcesPanel extends NodeManagerPanel {
    private Collection<FileUpload> uploads = new ArrayList<FileUpload>();
    private boolean overwrite = false;

    public UploadResourcesPanel(String id, IModel<BrixNode> model, final SimpleCallback goBack) {
        super(id, model);

        Form<?> form = new Form<UploadResourcesPanel>("form", new CompoundPropertyModel<UploadResourcesPanel>(this));
        add(form);

        form.add(new ContainerFeedbackPanel("feedback", this));

        form.add(new SubmitLink("upload") {
            @Override
            public void onSubmit() {
                processUploads();
            }
        });

        form.add(new Link<Void>("cancel") {
            @Override
            public void onClick() {
                goBack.execute();
            }
        });

        form.add(new MultiFileUploadField("uploads"));
        form.add(new CheckBox("overwrite"));
    }

    private void processUploads() {
        final BrixNode parentNode = getModelObject();

        for (final FileUpload upload : uploads) {
            final String fileName = upload.getClientFileName();

            if (parentNode.hasNode(fileName)) {
                if (overwrite) {
                    parentNode.getNode(fileName).remove();
                } else {
                    class ModelObject implements Serializable {
                        @SuppressWarnings("unused")
                        private String fileName = upload.getClientFileName();
                    }

                    getSession().error(getString("fileExists", new Model<ModelObject>(new ModelObject())));
                    continue;
                }
            }

            BrixNode newNode = (BrixNode) parentNode.addNode(fileName, "nt:file");

            String mime = upload.getContentType();

            BrixFileNode file = BrixFileNode.initialize(newNode, mime);
            Binary binary = createUploadBinary(upload, file.getSession().getValueFactory());
            setUploadData(file, binary);
            String encoding = resolveUploadEncoding(mime);
            if (encoding != null) {
                file.setEncoding(encoding);
            }
            file.getParent().save();
        }


        SitePlugin.get().selectNode(this, parentNode, true);
    }

    static Binary createUploadBinary(FileUpload upload, JcrValueFactory valueFactory) {
        try (InputStream input = upload.getInputStream()) {
            return valueFactory.createBinary(input);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            upload.closeStreams();
        }
    }

    static void setUploadData(BrixFileNode file, Binary binary) {
        try {
            file.setData(binary);
        } finally {
            binary.dispose();
        }
    }

    static String resolveUploadEncoding(String contentType) {
        if (contentType == null || !BrixFileNode.isText(ResourceNodePlugin.normalizeMimeType(contentType))) {
            return null;
        }

        String[] parameters = contentType.split(";");
        for (int i = 1; i < parameters.length; i++) {
            String parameter = parameters[i].trim();
            int equals = parameter.indexOf('=');
            if (equals < 0 || !"charset".equalsIgnoreCase(parameter.substring(0, equals).trim())) {
                continue;
            }

            String encoding = parameter.substring(equals + 1).trim();
            if (encoding.length() >= 2 && encoding.charAt(0) == '"'
                    && encoding.charAt(encoding.length() - 1) == '"') {
                encoding = encoding.substring(1, encoding.length() - 1);
            }
            try {
                return Charset.forName(encoding).name();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected void onDetach() {
        uploads.clear();
        super.onDetach();
    }
}
