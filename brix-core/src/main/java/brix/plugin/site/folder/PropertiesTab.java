package brix.plugin.site.folder;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

import brix.jcr.wrapper.BrixNode;
import brix.web.ContainerFeedbackPanel;
import brix.web.model.ModelBuffer;
import brix.web.picker.reference.ReferenceEditorConfiguration;
import brix.web.picker.reference.ReferenceEditorPanel;

public class PropertiesTab extends Panel<BrixNode>
{

    public PropertiesTab(String id, final IModel<BrixNode> folderNodeModel)
    {
        super(id, folderNodeModel);

        final ModelBuffer buffer = new ModelBuffer(folderNodeModel);

        Form<Void> form = new Form<Void>("form");
        
        form.add(new SubmitLink<Void>("submit")
        {
            @Override
            public void onSubmit()
            {
                buffer.apply();
                folderNodeModel.getObject().save();
                getSession().info(PropertiesTab.this.getString("propertiesSaved"));
            }
        });
        add(form);

        add(new ContainerFeedbackPanel("feedback", this));

        ReferenceEditorConfiguration conf = new ReferenceEditorConfiguration();

        conf.setDisplayFiles(true);
        conf.setWorkspaceName(folderNodeModel);

        form.add(new ReferenceEditorPanel("redirectReference", buffer
                .forProperty("redirectReference")).setConfiguration(conf));

    }
}
