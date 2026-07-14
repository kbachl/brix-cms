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

package org.brixcms;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.jcr.Session;

import org.apache.wicket.ThreadContext;
import org.apache.wicket.request.IExceptionMapper;
import org.apache.wicket.request.IRequestHandler;
import org.apache.wicket.request.IRequestMapper;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.cycle.RequestCycleContext;
import org.brixcms.auth.AuthorizationStrategy;
import org.brixcms.config.BrixConfig;
import org.brixcms.jcr.JcrSessionFactory;
import org.brixcms.jcr.api.JcrSession;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

public class BrixCurrentSessionTest {
    @After
    public void detachThreadContext() {
        ThreadContext.detach();
    }

    @Test
    public void reusesWrapperForSameRawSessionWithinRequest() {
        TestSessionFactory sessionFactory = new TestSessionFactory(EasyMock.createNiceMock(Session.class));
        TestBrix brix = new TestBrix(sessionFactory);
        bindRequestCycle();

        JcrSession first = brix.getCurrentSession("workspace");
        JcrSession second = brix.getCurrentSession("workspace");

        assertSame(first, second);
    }

    @Test
    public void doesNotReuseWrapperWithoutRequestCycle() {
        TestSessionFactory sessionFactory = new TestSessionFactory(EasyMock.createNiceMock(Session.class));
        TestBrix brix = new TestBrix(sessionFactory);

        JcrSession first = brix.getCurrentSession("workspace");
        JcrSession second = brix.getCurrentSession("workspace");

        assertNotSame(first, second);
    }

    @Test
    public void doesNotReuseWrapperAcrossRequests() {
        TestSessionFactory sessionFactory = new TestSessionFactory(EasyMock.createNiceMock(Session.class));
        TestBrix brix = new TestBrix(sessionFactory);
        bindRequestCycle();
        JcrSession first = brix.getCurrentSession("workspace");

        ThreadContext.detach();
        bindRequestCycle();
        JcrSession second = brix.getCurrentSession("workspace");

        assertNotSame(first, second);
    }

    @Test
    public void doesNotReuseWrapperWhenRawSessionChanges() {
        TestSessionFactory sessionFactory = new TestSessionFactory(EasyMock.createNiceMock(Session.class));
        TestBrix brix = new TestBrix(sessionFactory);
        bindRequestCycle();
        JcrSession first = brix.getCurrentSession("workspace");

        sessionFactory.setCurrentSession(EasyMock.createNiceMock(Session.class));
        JcrSession second = brix.getCurrentSession("workspace");

        assertNotSame(first, second);
    }

    @Test
    public void differentBrixInstancesDoNotShareWrappers() {
        Session rawSession = EasyMock.createNiceMock(Session.class);
        TestBrix firstBrix = new TestBrix(new TestSessionFactory(rawSession));
        TestBrix secondBrix = new TestBrix(new TestSessionFactory(rawSession));
        bindRequestCycle();

        JcrSession first = firstBrix.getCurrentSession("workspace");
        JcrSession second = secondBrix.getCurrentSession("workspace");

        assertNotSame(first, second);
    }

    private static void bindRequestCycle() {
        Request request = new Request() {
            private final Url url = Url.parse("");

            @Override
            public Url getUrl() {
                return url;
            }

            @Override
            public Url getClientUrl() {
                return url;
            }

            @Override
            public Locale getLocale() {
                return Locale.ROOT;
            }

            @Override
            public Charset getCharset() {
                return StandardCharsets.UTF_8;
            }

            @Override
            public Object getContainerRequest() {
                return null;
            }
        };
        Response response = new Response() {
            @Override
            public void write(CharSequence sequence) {
            }

            @Override
            public void write(byte[] array) {
            }

            @Override
            public void write(byte[] array, int offset, int length) {
            }

            @Override
            public String encodeURL(CharSequence url) {
                return url.toString();
            }

            @Override
            public Object getContainerResponse() {
                return null;
            }
        };
        IRequestMapper requestMapper = new IRequestMapper() {
            @Override
            public IRequestHandler mapRequest(Request request) {
                return null;
            }

            @Override
            public int getCompatibilityScore(Request request) {
                return 0;
            }

            @Override
            public Url mapHandler(IRequestHandler requestHandler) {
                return null;
            }
        };
        IExceptionMapper exceptionMapper = exception -> null;
        ThreadContext.setRequestCycle(new RequestCycle(
                new RequestCycleContext(request, response, requestMapper, exceptionMapper)));
    }

    private static class TestBrix extends Brix {
        private TestBrix(JcrSessionFactory sessionFactory) {
            super(new BrixConfig(sessionFactory, null, null));
        }

        @Override
        public AuthorizationStrategy newAuthorizationStrategy() {
            return null;
        }
    }

    private static class TestSessionFactory implements JcrSessionFactory {
        private Session currentSession;

        private TestSessionFactory(Session currentSession) {
            this.currentSession = currentSession;
        }

        private void setCurrentSession(Session currentSession) {
            this.currentSession = currentSession;
        }

        @Override
        public Session createSession(String workspace) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Session getCurrentSession(String workspace) {
            return currentSession;
        }
    }
}
