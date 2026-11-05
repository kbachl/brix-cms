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

package org.brixcms.plugin.site.picker.reference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.brixcms.web.reference.Reference;
import org.junit.Test;

public class ReferenceEditorPanelTest {
    @Test
    public void onlyOkCommitsEditsAndEveryCloseRefreshesTheParent() {
        WicketTester tester = new WicketTester();
        try {
            Reference original = new Reference();
            original.setType(Reference.Type.URL);
            original.setUrl("https://example.org/original");
            Model<Reference> model = Model.of(original);
            int[] updates = { 0 };
            ReferenceEditorPanel editor = new ReferenceEditorPanel("editor", model) {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    updates[0]++;
                }
            };
            editor.setConfiguration(new ReferenceEditorConfiguration().setAllowNodePicker(false).setAllowURLEdit(false));
            tester.startComponentInPage(editor);

            String[] cancelPaths = { "overlay:dialog:content:cancel", "overlay:close", "overlay" };
            for (int i = 0; i < cancelPaths.length; i++) {
                tester.executeAjaxEvent("editor:edit", "click");
                ReferenceEditorModalDialog dialog = editor.getModalDialog();
                ReferenceEditorWithButtons content = (ReferenceEditorWithButtons) dialog.get("overlay:dialog:content");
                assertEquals(original.getUrl(), content.getEditedReference().getUrl());
                content.getEditedReference().setUrl("https://example.org/discarded");
                tester.executeAjaxEvent("editor:modalDialog:" + cancelPaths[i], i == 2 ? "keydown" : "click");
                assertFalse(dialog.isOpen());
                assertSame(original, model.getObject());
                assertEquals("https://example.org/original", model.getObject().getUrl());
                assertEquals(i + 1, updates[0]);
                tester.assertComponentOnAjaxResponse(editor);
            }

            tester.executeAjaxEvent("editor:edit", "click");
            ReferenceEditorWithButtons content = (ReferenceEditorWithButtons) editor.getModalDialog().get("overlay:dialog:content");
            content.getEditedReference().setUrl("https://example.org/saved");
            tester.executeAjaxEvent("editor:modalDialog:overlay:dialog:content:ok", "click");
            assertFalse(editor.getModalDialog().isOpen());
            assertEquals("https://example.org/saved", model.getObject().getUrl());
            assertEquals(4, updates[0]);
            tester.assertComponentOnAjaxResponse(editor);
        } finally {
            tester.destroy();
        }
    }
}
