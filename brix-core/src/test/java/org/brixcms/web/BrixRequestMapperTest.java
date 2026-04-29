package org.brixcms.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.jcr.Node;

import org.apache.wicket.model.IModel;
import org.apache.wicket.protocol.https.HttpsConfig;
import org.apache.wicket.protocol.https.Scheme;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestCycle;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.web.nodepage.BrixNodePageRequestHandler;
import org.brixcms.web.nodepage.BrixNodeRequestHandler;
import org.brixcms.web.nodepage.BrixNodeWebPage;
import org.brixcms.web.nodepage.BrixPageParameters;
import org.easymock.EasyMock;
import org.junit.Test;

public class BrixRequestMapperTest {
    @Test
    public void desiredSchemeUsesNodeModelWithoutInstantiatingPage() {
        ExposedBrixRequestMapper mapper = new ExposedBrixRequestMapper();
        CountingNodeModel model = new CountingNodeModel(new ProtocolNode(BrixNode.Protocol.HTTPS));

        BrixNodePageRequestHandler handler = new BrixNodePageRequestHandler(model,
                new BrixNodePageRequestHandler.PageFactory() {
                    @Override
                    public BrixNodeWebPage newPage() {
                        throw new AssertionError("Protocol lookup must not instantiate the Wicket page");
                    }

                    @Override
                    public BrixPageParameters getPageParameters() {
                        return new BrixPageParameters();
                    }
                });

        assertEquals(Scheme.HTTPS, mapper.desiredSchemeFor(handler));
        assertEquals(1, model.loads);
    }

    @Test
    public void nodeRequestHandlerDetachesModel() {
        CountingNodeModel model = new CountingNodeModel(new ProtocolNode(BrixNode.Protocol.PRESERVE_CURRENT));

        new BrixNodeRequestHandler(model).detach(EasyMock.createNiceMock(IRequestCycle.class));

        assertTrue(model.detached);
    }

    private static class ExposedBrixRequestMapper extends BrixRequestMapper {
        private ExposedBrixRequestMapper() {
            super(null, new HttpsConfig(80, 443));
        }

        private Scheme desiredSchemeFor(IRequestHandler handler) {
            return getDesiredSchemeFor(handler);
        }
    }

    private static class CountingNodeModel implements IModel<BrixNode> {
        private final BrixNode node;
        private int loads;
        private boolean detached;

        private CountingNodeModel(BrixNode node) {
            this.node = node;
        }

        @Override
        public BrixNode getObject() {
            loads++;
            return node;
        }

        @Override
        public void detach() {
            detached = true;
        }
    }

    private static class ProtocolNode extends BrixNode {
        private final Protocol protocol;

        private ProtocolNode(Protocol protocol) {
            super(EasyMock.createNiceMock(Node.class), EasyMock.createNiceMock(JcrSession.class));
            this.protocol = protocol;
        }

        @Override
        public Protocol getRequiredProtocol() {
            return protocol;
        }
    }
}
