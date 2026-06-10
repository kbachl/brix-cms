package org.brixcms.plugin.site.resource;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.Test;

public class ResourceNodeHandlerTest {
    @Test
    public void contentETagUsesSha256Hash() {
        String etag = ResourceNodeHandler.createContentETag("abc123");

        assertEquals("W/\"sha256-abc123\"", etag);
    }

    @Test
    public void emptyContentHashDoesNotCreateETag() {
        assertNull(ResourceNodeHandler.createContentETag(""));
        assertNull(ResourceNodeHandler.createContentETag(null));
    }

    @Test
    public void ifNoneMatchAcceptsWeakAndStrongFormOfContentETag() {
        String etag = ResourceNodeHandler.createContentETag("abc123");

        assertTrue(ResourceNodeHandler.matchesETag("W/\"sha256-abc123\"", etag));
        assertTrue(ResourceNodeHandler.matchesETag("\"sha256-abc123\"", etag));
    }

    @Test
    public void ifModifiedSinceIsIgnoredWhenContentETagExists() {
        HttpServletRequest request = request(Map.of("If-Modified-Since", "Wed, 10 Jun 2026 12:00:00 GMT"),
                new Date().getTime());

        boolean notModified = ResourceNodeHandler.isNotModified(request, new Date(0),
                ResourceNodeHandler.createContentETag("abc123"));

        assertFalse(notModified);
    }

    @Test
    public void ifModifiedSinceIsFallbackWhenNoContentETagExists() {
        long modified = 1_000L;
        HttpServletRequest request = request(Map.of("If-Modified-Since", "Wed, 10 Jun 2026 12:00:00 GMT"),
                modified);

        boolean notModified = ResourceNodeHandler.isNotModified(request, new Date(modified), null);

        assertTrue(notModified);
    }

    private static HttpServletRequest request(Map<String, String> headers, long dateHeader) {
        Map<String, String> copy = new HashMap<String, String>(headers);
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if ("getHeader".equals(method.getName()) && args != null && args.length == 1) {
                return copy.get(args[0]);
            }
            if ("getDateHeader".equals(method.getName()) && args != null && args.length == 1) {
                return dateHeader;
            }
            return defaultValue(method.getReturnType());
        };
        return (HttpServletRequest) Proxy.newProxyInstance(ResourceNodeHandlerTest.class.getClassLoader(),
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
}
