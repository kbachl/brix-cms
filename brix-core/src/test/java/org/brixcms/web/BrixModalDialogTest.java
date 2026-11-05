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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.apache.wicket.extensions.ajax.markup.html.modal.ModalDialog;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.Model;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BrixModalDialogTest {
    private WicketTester tester;

    @Before
    public void setUp() {
        tester = new WicketTester();
        tester.getSession().setLocale(Locale.GERMAN);
    }

    @After
    public void tearDown() {
        tester.destroy();
    }

    @Test
    public void rendersAccessibleTitleAndClosesTemporaryContent() {
        BrixModalDialog dialog = new BrixModalDialog("modal");
        dialog.setTitle(Model.of("A <title>"));
        dialog.open(new Label(ModalDialog.CONTENT_ID, "Body"), null);
        tester.startComponentInPage(dialog);

        String html = tester.getLastResponseAsString();
        assertTrue(html.contains("A &lt;title&gt;"));
        assertTrue(html.contains("aria-modal=\"true\""));
        assertTrue(html.contains("aria-labelledby=\"" + dialog.get("overlay:title").getMarkupId() + "\""));
        assertTrue(html.contains("aria-label=\"Schließen\""));

        tester.executeAjaxEvent("modal:overlay:close", "click");
        assertFalse(dialog.isOpen());
        assertNull(dialog.get("overlay:dialog:content"));

        dialog.open(new Label(ModalDialog.CONTENT_ID, "Reopened"), null);
        tester.startComponentInPage(dialog);
        tester.assertContains("Reopened");
        tester.executeAjaxEvent("modal:overlay:close", "click");
        assertFalse(dialog.isOpen());
    }

    @Test
    public void closingNestedDialogLeavesOuterDialogOpen() {
        BrixModalDialog outer = new BrixModalDialog("modal");
        BrixModalDialog inner = new BrixModalDialog(ModalDialog.CONTENT_ID);
        outer.setTitle(Model.of("Outer"));
        inner.setTitle(Model.of("Inner"));
        inner.open(new Label(ModalDialog.CONTENT_ID, "Nested content"), null);
        outer.open(inner, null);
        tester.startComponentInPage(outer);

        tester.executeAjaxEvent("modal:overlay:dialog:content:overlay", "keydown");
        assertFalse(inner.isOpen());
        assertTrue(outer.isOpen());
        tester.assertComponentOnAjaxResponse(inner);
    }
}
