package org.brixcms.plugin.site.resource;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.Test;

public class StreamerTest {
    @Test
    public void suffixRangeLargerThanResourceReturnsWholeResourceWithCorrectHeaders() {
        byte[] data = bytes("abcdef");
        CapturingResponse response = new CapturingResponse();

        new Streamer(data.length, new ByteArrayInputStream(data), "asset.txt", false,
                request("bytes=-100"), response.asHttpServletResponse()).stream();

        assertEquals(HttpServletResponse.SC_PARTIAL_CONTENT, response.status);
        assertEquals("bytes 0-5/6", response.headers.get("Content-Range"));
        assertEquals(6, response.contentLength);
        assertArrayEquals(data, response.body.toByteArray());
    }

    @Test
    public void rangeEndBeyondResourceLengthIsClamped() {
        byte[] data = bytes("abcdef");
        CapturingResponse response = new CapturingResponse();

        new Streamer(data.length, new ByteArrayInputStream(data), "asset.txt", false,
                request("bytes=2-99"), response.asHttpServletResponse()).stream();

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
                request("bytes=4-7"), response.asHttpServletResponse()).stream();

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
                request("bytes=99-100"), response.asHttpServletResponse()).stream();

        assertEquals(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE, response.status);
        assertEquals("bytes */6", response.headers.get("Content-Range"));
        assertEquals(0, response.contentLength);
        assertEquals(0, response.body.size());
    }

    @Test
    public void unexpectedEndOfSourceStreamIsNotSilentlyIgnored() {
        byte[] data = bytes("abc");
        CapturingResponse response = new CapturingResponse();

        try {
            new Streamer(data.length + 2, new ByteArrayInputStream(data), "asset.txt", false,
                    request(null), response.asHttpServletResponse()).stream();
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

    private static class CapturingResponse {
        private int status;
        private long contentLength = -1;
        private final Map<String, String> headers = new HashMap<String, String>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        private HttpServletResponse asHttpServletResponse() {
            InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
                switch (method.getName()) {
                    case "setStatus":
                        status = (Integer) args[0];
                        return null;
                    case "setHeader":
                        headers.put((String) args[0], (String) args[1]);
                        return null;
                    case "setContentLengthLong":
                        contentLength = (Long) args[0];
                        return null;
                    case "getOutputStream":
                        return new ServletOutputStream() {
                            @Override
                            public boolean isReady() {
                                return true;
                            }

                            @Override
                            public void setWriteListener(WriteListener writeListener) {
                            }

                            @Override
                            public void write(int b) {
                                body.write(b);
                            }

                            @Override
                            public void write(byte[] b, int off, int len) {
                                body.write(b, off, len);
                            }
                        };
                    case "flushBuffer":
                        return null;
                    default:
                        return defaultValue(method.getReturnType());
                }
            };
            return (HttpServletResponse) Proxy.newProxyInstance(StreamerTest.class.getClassLoader(),
                    new Class<?>[] { HttpServletResponse.class }, handler);
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

}
