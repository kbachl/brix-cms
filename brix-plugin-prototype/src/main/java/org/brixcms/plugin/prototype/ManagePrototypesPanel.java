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

package org.brixcms.plugin.prototype;

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalDialog;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;
import org.brixcms.Brix;
import org.brixcms.auth.Action;
import org.brixcms.auth.Action.Context;
import org.brixcms.plugin.prototype.auth.CreatePrototypeAction;
import org.brixcms.plugin.prototype.auth.DeletePrototypeAction;
import org.brixcms.plugin.prototype.auth.RestorePrototypeAction;
import org.brixcms.web.BrixFeedbackPanel;
import org.brixcms.web.BrixModalDialog;
import org.brixcms.web.generic.BrixGenericPanel;
import org.brixcms.workspace.Workspace;
import org.brixcms.workspace.WorkspaceModel;

public class ManagePrototypesPanel extends BrixGenericPanel<Workspace> {
    private String prototypeName;
    private Component dialogOpener;

    public ManagePrototypesPanel(String id, final IModel<Workspace> model) {
        super(id, model);
        setOutputMarkupId(true);

        IModel<List<Workspace>> prototypesModel = new LoadableDetachableModel<List<Workspace>>() {
            @Override
            protected List<Workspace> load() {
                List<Workspace> list = PrototypePlugin.get().getPrototypes();
                return getBrix().filterVisibleWorkspaces(list, Context.ADMINISTRATION);
            }
        };

        Form<Void> modalDialogForm = new Form<Void>("modalDialogForm");
        add(modalDialogForm);

        final BrixModalDialog modalDialog = new BrixModalDialog("modalDialog") {
            @Override
            public ModalDialog close(AjaxRequestTarget target) {
                if (isOpen()) {
                    super.close(target);
                    if (target != null) {
                        target.add(ManagePrototypesPanel.this);
                        target.focusComponent(dialogOpener);
                    }
                }
                return this;
            }
        };
        modalDialogForm.add(modalDialog);

        add(new ListView<Workspace>("prototypes", prototypesModel) {
            @Override
            protected IModel<Workspace> getListItemModel(IModel<? extends List<Workspace>> listViewModel, int index) {
                return new WorkspaceModel(listViewModel.getObject().get(index));
            }

            @Override
            protected void populateItem(final ListItem<Workspace> item) {
                PrototypePlugin plugin = PrototypePlugin.get();
                final String name = plugin.getUserVisibleName(item.getModelObject(), false);

                item.add(new Label("label", name));
                item.add(new Link<Void>("browse") {
                    @Override
                    public void onClick() {
                        model.setObject(item.getModelObject());
                    }
                });

                item.add(new AjaxLink<Void>("restoreItems") {
                    @Override
                    public void onClick(AjaxRequestTarget target) {
                        String prototypeId = item.getModelObject().getId();
                        String targetId = ManagePrototypesPanel.this.getModelObject().getId();
                        Panel panel = new RestoreItemsPanel(ModalDialog.CONTENT_ID, prototypeId, targetId);
                        modalDialog.setTitle(new ResourceModel("selectItems"));
                        dialogOpener = this;
                        modalDialog.open(panel, target);
                    }

                    @Override
                    public boolean isVisible() {
                        Workspace target = ManagePrototypesPanel.this.getModelObject();
                        Action action = new RestorePrototypeAction(Context.ADMINISTRATION, item.getModelObject(), target);
                        return getBrix().getAuthorizationStrategy().isActionAuthorized(action);
                    }
                });

                item.add(new Link<Void>("delete") {
                    @Override
                    public void onClick() {
                        Workspace prototype = item.getModelObject();
                        prototype.delete();
                    }

                    @Override
                    public boolean isVisible() {
                        Action action = new DeletePrototypeAction(Context.ADMINISTRATION, item.getModelObject());
                        return getBrix().getAuthorizationStrategy().isActionAuthorized(action);
                    }
                });
            }
        });

        Form<Object> form = new Form<Object>("form") {
            @Override
            public boolean isVisible() {
                Workspace current = ManagePrototypesPanel.this.getModelObject();
                Action action = new CreatePrototypeAction(Context.ADMINISTRATION, current);
                return getBrix().getAuthorizationStrategy().isActionAuthorized(action);
            }
        };

        TextField<String> prototypeName = new TextField<String>("prototypeName", new PropertyModel<String>(this, "prototypeName"));
        form.add(prototypeName);

        prototypeName.setRequired(true);
        prototypeName.add(new UniquePrototypeNameValidator());

        final FeedbackPanel feedback;

        add(feedback = new BrixFeedbackPanel("feedback"));
        feedback.setOutputMarkupId(true);

        form.add(new AjaxButton("submit") {
            @Override
            public void onSubmit(AjaxRequestTarget target) {
                String workspaceId = ManagePrototypesPanel.this.getModelObject().getId();
                CreatePrototypePanel panel = new CreatePrototypePanel(ModalDialog.CONTENT_ID, workspaceId,
                        ManagePrototypesPanel.this.prototypeName);
                modalDialog.setTitle(new ResourceModel("selectItemsToCreate"));
                dialogOpener = this;
                modalDialog.open(panel, target);
            }

            @Override
            protected void onError(AjaxRequestTarget target) {
                target.add(feedback);
            }
        });

        add(form);
    }

    private Brix getBrix() {
        // TODO: We don't really have a node here
        return Brix.get();
    }

    private class UniquePrototypeNameValidator implements IValidator<String> {
        public void validate(IValidatable<String> validatable) {
            String name = validatable.getValue();
            if (PrototypePlugin.get().prototypeExists(name)) {
                validatable.error(new ValidationError().addKey("UniquePrototypeNameValidator"));
            }
        }
    }
}
