package org.brixcms.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import javax.jcr.Node;

import org.apache.wicket.WicketRuntimeException;
import org.apache.wicket.core.request.handler.PageProvider;
import org.apache.wicket.core.request.handler.RenderPageRequestHandler;
import org.apache.wicket.core.request.mapper.IPageSource;
import org.apache.wicket.model.IModel;
import org.apache.wicket.mock.MockWebRequest;
import org.apache.wicket.protocol.http.PageExpiredException;
import org.apache.wicket.protocol.https.HttpsConfig;
import org.apache.wicket.protocol.https.Scheme;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestCycle;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.component.IRequestablePage;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.http.WebRequest;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.brixcms.Brix;
import org.brixcms.Path;
import org.brixcms.auth.AuthorizationStrategy;
import org.brixcms.config.BrixConfig;
import org.brixcms.config.PrefixUriMapper;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.plugin.site.page.PageRenderingPage;
import org.brixcms.web.nodepage.BrixNodePageRequestHandler;
import org.brixcms.web.nodepage.BrixNodeRequestHandler;
import org.brixcms.web.nodepage.BrixNodeWebPage;
import org.brixcms.web.nodepage.BrixPageParameters;
import org.brixcms.workspace.Workspace;
import org.easymock.EasyMock;
import org.junit.Test;

public class BrixRequestMapperTest {
    @Test
    public void malformedNamespaceNameIsDetectedBeforeJcrLookup() {
        Path malformedPath = new Path(
                "/assets/js/Et.href,ce.extend%28%7Bactive:0,ajaxSettings:%7Burl:Et.href,isLocal:/%5E%28");

        assertTrue(BrixRequestMapper.hasMalformedJcrName(malformedPath));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/:content/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/foo:bar:baz/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/1foo:bar/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/content:/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/{urn:example}/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/{urn:example}content:part/page")));
        assertTrue(BrixRequestMapper.hasMalformedJcrName(new Path("/{foo/bar}page")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/brix:root/content")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/_custom:content/page")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/{foo}bar/page")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/{urn:example}content/page")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/{urn:example/path}content/page")));
        assertFalse(BrixRequestMapper.hasMalformedJcrName(new Path("/content/products")));
    }

    @Test
    public void jcrValidationIsAppliedAfterUriMapping() {
        PrefixUriMapper uriMapper = new PrefixUriMapper(new Path("/cms:")) {
            @Override
            public Workspace getWorkspaceForRequest(RequestCycle requestCycle, Brix brix) {
                return null;
            }
        };
        BrixRequestMapper requestMapper = new BrixRequestMapper(new TestBrix(uriMapper), new HttpsConfig(80, 443));

        assertEquals(new Path("/page"), requestMapper.getValidNodePathForUriPath(new Path("/cms:/page")));
        assertEquals(new Path("/{urn:example}content"),
                requestMapper.getValidNodePathForUriPath(new Path("/cms:/{urn:example}content")));
        assertNull(requestMapper.getValidNodePathForUriPath(new Path("/cms:/:content")));
        assertNull(requestMapper.getValidNodePathForUriPath(new Path("/cms:/{foo/bar}page")));
    }

    @Test
    public void refererIsReadFromWebRequestInsteadOfContainerRequest() {
        MockWebRequest request = new MockWebRequest(Url.parse("products")) {
            @Override
            public Object getContainerRequest() {
                throw new AssertionError("The servlet container request must not be read as a Wicket WebRequest");
            }
        };
        request.setHeader(WebRequest.HEADER_REFERER, "https://example.test/products");

        assertEquals("https://example.test/products", BrixRequestMapper.getReferer(request));
    }

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

    @Test
    public void expiredPageRenderingPageWithoutBrixFactoryBecomesPageExpired() {
        WicketRuntimeException recreationFailure = missingPageRenderingPageConstructor();
        RenderPageRequestHandler handler = expiredPageRenderingHandler(recreationFailure);
        BrixRequestMapper mapper = new BrixRequestMapper(null, new HttpsConfig(80, 443));

        PageExpiredException result = assertThrows(PageExpiredException.class, () -> mapper.mapHandler(handler));

        assertSame(recreationFailure, result.getCause());
    }

    @Test
    public void newPageRenderingPageConstructionFailureRemainsVisible() {
        WicketRuntimeException recreationFailure = missingPageRenderingPageConstructor();
        PageProvider provider = new PageProvider(PageRenderingPage.class, new PageParameters());
        provider.setPageSource(failingPageSource(recreationFailure));
        RenderPageRequestHandler handler = new RenderPageRequestHandler(provider);
        BrixRequestMapper mapper = new BrixRequestMapper(null, new HttpsConfig(80, 443));

        WicketRuntimeException result = assertThrows(WicketRuntimeException.class, () -> mapper.mapHandler(handler));

        assertSame(recreationFailure, result);
    }

    private static RenderPageRequestHandler expiredPageRenderingHandler(WicketRuntimeException recreationFailure) {
        PageProvider provider = new PageProvider(42, PageRenderingPage.class, new PageParameters(), 1);
        provider.setPageSource(failingPageSource(recreationFailure));
        return new RenderPageRequestHandler(provider);
    }

    private static IPageSource failingPageSource(WicketRuntimeException recreationFailure) {
        return new IPageSource() {
            @Override
            public IRequestablePage getPageInstance(int pageId) {
                return null;
            }

            @Override
            public IRequestablePage newPageInstance(Class<? extends IRequestablePage> pageClass,
                    PageParameters pageParameters) {
                throw recreationFailure;
            }
        };
    }

    private static WicketRuntimeException missingPageRenderingPageConstructor() {
        return new WicketRuntimeException("Unable to create page from class " + PageRenderingPage.class.getName(),
                new NoSuchMethodException(PageRenderingPage.class.getName() + ".<init>()"));
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

    private static class TestBrix extends Brix {
        private TestBrix(PrefixUriMapper uriMapper) {
            super(new BrixConfig(null, null, uriMapper));
        }

        @Override
        public AuthorizationStrategy newAuthorizationStrategy() {
            return null;
        }
    }
}
