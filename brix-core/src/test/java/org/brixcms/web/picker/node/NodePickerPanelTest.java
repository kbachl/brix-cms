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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.util.tester.WicketTester;
import org.brixcms.BrixNodeModel;
import org.brixcms.web.picker.common.RootTreeNode;
import org.brixcms.web.tree.JcrTreeNode;
import org.junit.Test;

public class NodePickerPanelTest {
    @Test
    public void everyClosePathRefreshesPickerAndReopeningCreatesFreshContent() {
        WicketTester tester = new WicketTester();
        try {
            int[] updates = { 0 };
            RootTreeNode root = new RootTreeNode("test") {
                @Override
                public List<? extends JcrTreeNode> getChildren() {
                    return List.of();
                }
            };
            NodePickerPanel picker = new NodePickerPanel("picker", new BrixNodeModel<>(), root, null, null) {
                @Override
                protected void onUpdate(AjaxRequestTarget target) {
                    updates[0]++;
                }
            };
            tester.startComponentInPage(picker);
            Component previous = null;
            String[] closePaths = { "overlay:dialog:content:cancel", "overlay:dialog:content:ok", "overlay:close", "overlay" };
            for (int i = 0; i < closePaths.length; i++) {
                tester.executeAjaxEvent("picker:edit", "click");
                NodePickerModalDialog dialog = picker.getModalDialog();
                Component content = dialog.get("overlay:dialog:content");
                assertNotSame(previous, content);
                previous = content;
                tester.executeAjaxEvent("picker:modalDialog:" + closePaths[i], i == 3 ? "keydown" : "click");
                assertFalse(dialog.isOpen());
                assertNull(dialog.get("overlay:dialog:content"));
                assertEquals(i + 1, updates[0]);
                tester.assertComponentOnAjaxResponse(picker);
            }
        } finally {
            tester.destroy();
        }
    }
}
