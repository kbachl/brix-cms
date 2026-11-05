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

package org.brixcms.web.picker.node;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalDialog;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.web.BrixModalDialog;
import org.brixcms.web.generic.IGenericComponent;
import org.brixcms.web.tree.JcrTreeNode;
import org.brixcms.web.tree.NodeFilter;

public class NodePickerModalDialog extends BrixModalDialog implements IGenericComponent<BrixNode> {
    private final JcrTreeNode rootNode;
    private final NodeFilter enabledFilter;
    private final NodeFilter visibilityFilter;

    public NodePickerModalDialog(String id, JcrTreeNode rootNode, NodeFilter visibilityFilter, NodeFilter enabledFilter) {
        super(id);

        this.rootNode = rootNode;
        this.enabledFilter = enabledFilter;
        this.visibilityFilter = visibilityFilter;

        init();
    }

    public NodePickerModalDialog(String id, IModel<BrixNode> model, JcrTreeNode rootNode, NodeFilter visibilityFilter,
            NodeFilter enabledFilter) {
        super(id);
        setModel(model);

        this.rootNode = rootNode;
        this.enabledFilter = enabledFilter;
        this.visibilityFilter = visibilityFilter;

        init();
    }

    private void init() {
        setTitle(new ResourceModel("node-picker-title"));
    }

    public BrixNode getModelObject() {
        return (BrixNode) getDefaultModelObject();
    }

    public void setModel(IModel<BrixNode> model) {
        setDefaultModel(model);
    }

    public void setModelObject(BrixNode object) {
        setDefaultModelObject(object);
    }

    @Override
    public ModalDialog open(AjaxRequestTarget target) {
        if (!isOpen()) {
            return super.open(newContent(), target);
        }
        return super.open(target);
    }

    private NodePickerWithButtons newContent() {
        return new NodePickerWithButtons(CONTENT_ID, getModel(), rootNode, visibilityFilter, enabledFilter) {
            @Override
            protected void onCancel(AjaxRequestTarget target) {
                super.onCancel(target);
                NodePickerModalDialog.this.onCancel(target);
            }

            @Override
            protected void onOk(AjaxRequestTarget target) {
                super.onOk(target);
                NodePickerModalDialog.this.onOk(target);
            }

            @Override
            public boolean isDisplayFiles() {
                return NodePickerModalDialog.this.isDisplayFiles();
            }
        };
    }

    @SuppressWarnings("unchecked")
    public IModel<BrixNode> getModel() {
        return (IModel<BrixNode>) getDefaultModel();
    }

    protected void onCancel(AjaxRequestTarget target) {
        close(target);
    }

    protected void onOk(AjaxRequestTarget target) {
        close(target);
    }

    public boolean isDisplayFiles() {
        return true;
    }
}
