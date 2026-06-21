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
    public void ifNoneMatchWildcardMatchesExistingResourceEvenWithoutServerETag() {
        // Regression: an existing resource whose hash has not been persisted yet has no server ETag, but a
        // client sending "If-None-Match: *" must still get a 304 - the wildcard matches resource existence
        // (RFC 7232), not a specific ETag. On the old code this returned false because the empty-etag guard
        // ran before the wildcard check.
        HttpServletRequest request = request(Map.of("If-None-Match", "*"), -1L);

        boolean notModified = ResourceNodeHandler.isNotModified(request, null, null);

        assertTrue(notModified);
    }

    @Test
    public void ifNoneMatchSpecificValueDoesNotMatchWhenServerHasNoETag() {
        // Without a server ETag a specific validator cannot be confirmed, so the request must not be a
        // 304 - it is served normally and the hash is backfilled for the next request.
        HttpServletRequest request = request(Map.of("If-None-Match", "\"sha256-abc123\""), -1L);

        boolean notModified = ResourceNodeHandler.isNotModified(request, null, null);

        assertFalse(notModified);
    }

    @Test
    public void ifModifiedSinceIsHonoredEvenWhenContentETagExists() {
        // No If-None-Match is sent. Even though the resource carries an ETag, the server must honor
        // If-Modified-Since for clients that rely on it (RFC 7232 precedence: If-None-Match only
        // takes precedence when actually present).
        HttpServletRequest request = request(Map.of("If-Modified-Since", "Wed, 10 Jun 2026 12:00:00 GMT"),
                new Date().getTime());

        boolean notModified = ResourceNodeHandler.isNotModified(request, new Date(0),
                ResourceNodeHandler.createContentETag("abc123"));

        assertTrue(notModified);
    }

    @Test
    public void ifModifiedSinceIsFallbackWhenNoContentETagExists() {
        long modified = 1_000L;
        HttpServletRequest request = request(Map.of("If-Modified-Since", "Wed, 10 Jun 2026 12:00:00 GMT"),
                modified);

        boolean notModified = ResourceNodeHandler.isNotModified(request, new Date(modified), null);

        assertTrue(notModified);
    }

    @Test
    public void ifRangeAbsentAllowsRange() {
        HttpServletRequest request = ifRangeRequest(null, false, -1L);

        boolean rangeAllowed = ResourceNodeHandler.ifRangeMatches(request,
                ResourceNodeHandler.createContentETag("abc123"), new Date(0));

        assertTrue(rangeAllowed);
    }

    @Test
    public void ifRangeEtagMatchAllowsRange() {
        String etag = ResourceNodeHandler.createContentETag("abc123");
        HttpServletRequest request = ifRangeRequest(etag, false, -1L);

        assertTrue(ResourceNodeHandler.ifRangeMatches(request, etag, new Date(0)));
    }

    @Test
    public void ifRangeEtagMismatchForbidsRange() {
        String current = ResourceNodeHandler.createContentETag("abc123");
        HttpServletRequest request = ifRangeRequest(ResourceNodeHandler.createContentETag("xyz"), false, -1L);

        // Validator does not match the current entity -> must serve full response, not a range, so the
        // client cannot splice bytes of the new version into a partial download of the old one.
        assertFalse(ResourceNodeHandler.ifRangeMatches(request, current, new Date(0)));
    }

    @Test
    public void ifRangeDateNotModifiedSinceAllowsRange() {
        // entity last modified at t=1000s; If-Range date t=2000s -> unchanged since -> range allowed
        HttpServletRequest request = ifRangeRequest("Wed, 10 Jun 2026 12:00:00 GMT", true, 2_000_000L);

        assertTrue(ResourceNodeHandler.ifRangeMatches(request,
                ResourceNodeHandler.createContentETag("abc123"), new Date(1_000_000L)));
    }

    @Test
    public void ifRangeDateModifiedSinceForbidsRange() {
        // entity last modified at t=3000s; If-Range date t=2000s -> changed since -> full response
        HttpServletRequest request = ifRangeRequest("Wed, 10 Jun 2026 12:00:00 GMT", true, 2_000_000L);

        assertFalse(ResourceNodeHandler.ifRangeMatches(request,
                ResourceNodeHandler.createContentETag("abc123"), new Date(3_000_000L)));
    }

    @Test
    public void ifRangeDateWithNullLastModifiedForbidsRange() {
        HttpServletRequest request = ifRangeRequest("Wed, 10 Jun 2026 12:00:00 GMT", true, 2_000_000L);

        assertFalse(ResourceNodeHandler.ifRangeMatches(request,
                ResourceNodeHandler.createContentETag("abc123"), null));
    }

    @Test
    public void legacyJavaScriptMimeIsNormalizedToTextJavascriptWithUtf8Charset() {
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("application/x-javascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("application/javascript", null));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("application/ecmascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("application/x-ecmascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/ecmascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/x-ecmascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/x-javascript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/jscript", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/livescript", "UTF-8"));
    }

    @Test
    public void versionedJavaScriptMimeIsNormalizedToTextJavascript() {
        // RFC 9239 deprecated the versioned text/javascript1.x forms (enumerates 1.0..1.5; tolerant of higher).
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/javascript1.5", "UTF-8"));
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/javascript1.7", "UTF-8"));
    }

    @Test
    public void standardTextJavascriptGetsCharsetOnly() {
        assertEquals("text/javascript; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/javascript", "UTF-8"));
    }

    @Test
    public void textCssAndHtmlGetUtf8Charset() {
        assertEquals("text/css; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/css", "UTF-8"));
        assertEquals("text/html; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("text/html", "UTF-8"));
    }

    @Test
    public void applicationXmlGetsCharset() {
        assertEquals("application/xml; charset=UTF-8",
                ResourceNodeHandler.normalizeContentType("application/xml", "UTF-8"));
    }

    @Test
    public void explicitlyDeclaredCharsetIsPreserved() {
        assertEquals("text/css; charset=windows-1252",
                ResourceNodeHandler.normalizeContentType("text/css; charset=windows-1252", "UTF-8"));
    }

    @Test
    public void binaryAndJsonTypesGetNoCharset() {
        assertEquals("image/png", ResourceNodeHandler.normalizeContentType("image/png", "UTF-8"));
        assertEquals("font/woff2", ResourceNodeHandler.normalizeContentType("font/woff2", "UTF-8"));
        assertEquals("application/octet-stream",
                ResourceNodeHandler.normalizeContentType("application/octet-stream", "UTF-8"));
        assertEquals("application/json",
                ResourceNodeHandler.normalizeContentType("application/json", "UTF-8"));
    }

    @Test
    public void nullMimeTypeFallsBackToOctetStream() {
        assertEquals("application/octet-stream", ResourceNodeHandler.normalizeContentType(null, "UTF-8"));
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

    /**
     * Builds a request that carries only an {@code If-Range} header. When {@code dateParseable} is
     * {@code true} the container parses it as an HTTP-date returning {@code ifRangeDate}; otherwise
     * parsing throws {@link IllegalArgumentException}, mimicking a container faced with an entity-tag
     * value (which the production code then treats as an entity-tag rather than a date).
     */
    private static HttpServletRequest ifRangeRequest(String ifRangeHeader, boolean dateParseable, long ifRangeDate) {
        Map<String, String> headers = new HashMap<String, String>();
        if (ifRangeHeader != null) {
            headers.put("If-Range", ifRangeHeader);
        }
        InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
            if ("getHeader".equals(method.getName()) && args != null && args.length == 1) {
                return headers.get(args[0]);
            }
            if ("getDateHeader".equals(method.getName()) && args != null && args.length == 1
                    && "If-Range".equals(args[0])) {
                if (!dateParseable) {
                    throw new IllegalArgumentException("not a valid HTTP-date");
                }
                return ifRangeDate;
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
