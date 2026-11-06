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
package org.brixcms.web;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalDialog;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.JavaScriptHeaderItem;
import org.apache.wicket.markup.head.OnDomReadyHeaderItem;
import org.apache.wicket.markup.head.PriorityHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.resource.CssResourceReference;
import org.apache.wicket.request.resource.JavaScriptResourceReference;

/** Brix title bar and sizing for Wicket's modal dialog. */
public class BrixModalDialog extends ModalDialog {
    private final Label title;

    public BrixModalDialog(String id) {
        super(id);
        closeOnEscape();
        trapFocus();

        WebMarkupContainer overlay = (WebMarkupContainer) get("overlay");
        title = new Label("title", "");
        title.setOutputMarkupId(true);
        overlay.add(title);
        overlay.add(AttributeModifier.replace("aria-labelledby", (IModel<String>) title::getMarkupId));
        overlay.add(new AjaxLink<Void>("close") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                close(target);
            }
        });
    }

    public void setTitle(IModel<String> model) {
        title.setDefaultModel(model);
    }

    @Override
    public void renderHead(IHeaderResponse response) {
        super.renderHead(response);
        response.render(CssHeaderItem.forReference(new CssResourceReference(BrixModalDialog.class, "BrixModalDialog.css")));
        response.render(JavaScriptHeaderItem.forReference(new JavaScriptResourceReference(BrixModalDialog.class, "BrixModalDialog.js")));
        if (isOpen()) {
            response.render(new PriorityHeaderItem(OnDomReadyHeaderItem.forScript(
                    "BrixModalDialog.mount('" + getMarkupId() + "');")));
        }
    }
}
