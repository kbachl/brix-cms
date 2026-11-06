package org.brixcms.web.picker.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.wicket.ajax.AbstractDefaultAjaxBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.settings.ExceptionSettings;
import org.apache.wicket.util.tester.WicketTester;
import org.brixcms.web.tree.JcrTreeNode;
import org.junit.Test;

public class NodePickerTreeGridPanelTest {
    @Test
    public void keepsRowIdsStableAcrossPartialAndFullAjaxUpdatesWithoutBodyMarkupWarnings() {
        WicketTester tester = new WicketTester();
        try {
            tester.getApplication().getExceptionSettings().setNotRenderableErrorStrategy(
                    ExceptionSettings.NotRenderableErrorStrategy.THROW_EXCEPTION);
            JcrTreeNode child = node();
            JcrTreeNode root = node(child);
            NodePickerTreeGridPanel picker = new NodePickerTreeGridPanel("picker", null, null) {
                @Override
                protected JcrTreeNode getRootNode() {
                    return root;
                }
            };
            tester.startComponentInPage(picker);
            var grid = picker.getGrid();
            var tree = grid.getTree();
            String bodyId = tree.getMarkupId();
            assertTrue(tree.getRenderBodyOnly());
            assertFalse(tree.getOutputMarkupId());
            assertTrue(tester.getLastResponseAsString().contains("id=\"" + bodyId + "_"));
            assertFalse(tester.getLastResponseAsString().contains("id=\"" + bodyId + "\""));

            AbstractDefaultAjaxBehavior update = new AbstractDefaultAjaxBehavior() {
                @Override
                protected void respond(AjaxRequestTarget target) {
                    if (tree.getTreeState().isNodeSelected(child)) {
                        tree.invalidateAll();
                    } else {
                        tree.getTreeState().selectNode(child, true);
                    }
                    tree.updateTree(target);
                }
            };
            grid.add(update);

            tester.executeBehavior(update);
            assertTrue(tester.getLastResponseAsString(),
                    tester.getLastResponseAsString().contains("<component id=\"" + bodyId + "_"));
            assertTrue(tree.getTreeState().isNodeSelected(child));
            assertFalse(tree.getOutputMarkupId());

            tester.executeBehavior(update);
            tester.assertComponentOnAjaxResponse(grid);
            assertEquals(bodyId, tree.getMarkupId());
            assertFalse(tree.getOutputMarkupId());
            assertTrue(tree.getTreeState().isNodeSelected(child));
        } finally {
            tester.destroy();
        }
    }

    private static JcrTreeNode node(JcrTreeNode... children) {
        return new RootTreeNode("test") {
            @Override
            public List<? extends JcrTreeNode> getChildren() {
                return List.of(children);
            }
        };
    }
}
