package org.brixcms.plugin.site.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.wicket.request.http.WebResponse;
import org.junit.Test;

public class StreamerTest {
    @Test
    public void suffixRangeLargerThanResourceReturnsWholeResourceWithCorrectHeaders() {
        byte[] data = bytes("abcdef");
        CapturingResponse response = new CapturingResponse();

        long written = new Streamer(data.length, new ByteArrayInputStream(data), "asset.txt", false,
                request("bytes=-100"), response).stream();

        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, response.status);
        assertEquals("bytes 0-5/6", response.headers.get("Content-Range"));
        assertEquals(6, response.contentLength);
        assertEquals(6, written);
        assertArrayEquals(data, response.body.toByteArray());
        assertFalse(response.flushed);
    }

    @Test
    public void rangeEndBeyondResourceLengthIsClamped() {
        byte[] data = bytes("abcdef");
        CapturingResponse response = new CapturingResponse();

        new Streamer(data.length, new ByteArrayInputStream(data), "asset.txt", false,
                request("bytes=2-99"), response).stream();

        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, response.status);
        assertEquals("bytes 2-5/6", response.headers.get("Content-Range"));
        assertEquals(4, response.contentLength);
        assertArrayEquals(bytes("cdef"), response.body.toByteArray());
    }

    @Test
    public void rangeStartIsSkippedFullyEvenWhenInputStreamSkipsPartially() {
        byte[] data = bytes("0123456789");
        CapturingResponse response = new CapturingResponse();

        new Streamer(data.length, new SlowSkipInputStream(data), "asset.txt", false,
                request("bytes=4-7"), response).stream();

        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, response.status);
        assertEquals("bytes 4-7/10", response.headers.get("Content-Range"));
        assertEquals(4, response.contentLength);
        assertArrayEquals(bytes("4567"), response.body.toByteArray());
    }

    @Test
    public void unsatisfiableRangeReturns416WithoutBody() {
        byte[] data = bytes("abcdef");
        CapturingResponse response = new CapturingResponse();

        new Streamer(data.length, new ByteArrayInputStream(data), "asset.txt", false,
                request("bytes=99-100"), response).stream();

        assertEquals(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, response.status);
        assertEquals("bytes */6", response.headers.get("Content-Range"));
        assertEquals(0, response.contentLength);
        assertEquals(0, response.body.size());
    }

    @Test
    public void suppressedBodySetsHeadersAndClosesSourceStream() {
        byte[] data = bytes("abcdef");
        CloseTrackingInputStream stream = new CloseTrackingInputStream(data);
        CapturingResponse response = new CapturingResponse();

        long written = new Streamer(data.length, stream, "asset.txt", false,
                request(null), response, false).stream();

        assertEquals(HttpServletResponse.SC_OK, response.status);
        assertEquals(data.length, response.contentLength);
        assertEquals(0, written);
        assertEquals(0, response.body.size());
        assertTrue(stream.closed);
        assertFalse(response.flushed);
    }

    @Test
    public void unexpectedEndOfSourceStreamIsNotSilentlyIgnored() {
        byte[] data = bytes("abc");
        CapturingResponse response = new CapturingResponse();

        try {
            new Streamer(data.length + 2, new ByteArrayInputStream(data), "asset.txt", false,
                    request(null), response).stream();
        } catch (RuntimeException e) {
            assertTrue(rootCause(e) instanceof EOFException);
            return;
        }

        throw new AssertionError("Expected EOFException wrapped in RuntimeException");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable result = t;
        while (result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static HttpServletRequest request(String range) {
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if ("getHeader".equals(method.getName()) && args != null && args.length == 1 && "Range".equals(args[0])) {
                return range;
            }
            return defaultValue(method.getReturnType());
        };
        return (HttpServletRequest) Proxy.newProxyInstance(StreamerTest.class.getClassLoader(),
                new Class<?>[] { HttpServletRequest.class }, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        return 0;
    }

    private static class CapturingResponse extends WebResponse {
        private int status;
        private long contentLength = -1;
        private boolean flushed;
        private final Map<String, String> headers = new HashMap<String, String>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public void addCookie(Cookie cookie) {
        }

        @Override
        public void clearCookie(Cookie cookie) {
        }

        @Override
        public boolean isHeaderSupported() {
            return true;
        }

        @Override
        public void setHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void setDateHeader(String name, Instant date) {
            headers.put(name, date.toString());
        }

        @Override
        public void setContentLength(long length) {
            contentLength = length;
        }

        @Override
        public void setContentType(String mimeType) {
            headers.put("Content-Type", mimeType);
        }

        @Override
        public void setStatus(int status) {
            this.status = status;
        }

        @Override
        public void sendError(int status, String message) {
            this.status = status;
        }

        @Override
        public String encodeRedirectURL(CharSequence url) {
            return url.toString();
        }

        @Override
        public void sendRedirect(String url) {
        }

        @Override
        public boolean isRedirect() {
            return false;
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void write(CharSequence sequence) {
            body.writeBytes(sequence.toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void write(byte[] array) {
            body.write(array, 0, array.length);
        }

        @Override
        public void write(byte[] array, int offset, int length) {
            body.write(array, offset, length);
        }

        @Override
        public String encodeURL(CharSequence url) {
            return url.toString();
        }

        @Override
        public Object getContainerResponse() {
            return null;
        }
    }

    private static class SlowSkipInputStream extends ByteArrayInputStream {
        private SlowSkipInputStream(byte[] data) {
            super(data);
        }

        @Override
        public synchronized long skip(long n) {
            return super.skip(Math.min(n, 1));
        }
    }

    private static class CloseTrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private CloseTrackingInputStream(byte[] data) {
            super(data);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

}
