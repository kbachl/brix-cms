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

package org.brixcms.markup;

import org.apache.wicket.Application;
import org.apache.wicket.MarkupContainer;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.web.generic.IGenericComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Contains {@link GeneratedMarkup} instances associated with {@link MarkupContainer}s. The {@link MarkupContainer}s
 * must also implement {@link MarkupSourceProvider} so that the cache can generate markup on demand and reuse it until
 * explicitly invalidated.
 *
 * @author Matej Knopp
 */
public class MarkupCache {
    private static final Logger log = LoggerFactory.getLogger(MarkupCache.class);
    private static final long WICKET_CACHE_CLEAR_THRESHOLD = 100_000;

    private final ConcurrentMap<String, ConcurrentMap<CacheKey, GeneratedMarkup>> workspaceCaches =
            new ConcurrentHashMap<String, ConcurrentMap<CacheKey, GeneratedMarkup>>();
    private final ConcurrentMap<String, ConcurrentMap<WicketCacheIdentity, String>> wicketCacheKeys =
            new ConcurrentHashMap<String, ConcurrentMap<WicketCacheIdentity, String>>();
    private final ConcurrentLinkedQueue<String> retiredWicketCacheKeys = new ConcurrentLinkedQueue<String>();
    private final AtomicBoolean drainingRetiredWicketCacheKeys = new AtomicBoolean();
    private final AtomicLong retiredWicketCacheKeysSinceClear = new AtomicLong();
    private final WicketMarkupRemover wicketMarkupRemover;
    private final BooleanSupplier wicketMarkupCacheClearer;

    public MarkupCache() {
        this(MarkupCache::removeWicketMarkup, MarkupCache::clearWicketMarkupCache);
    }

    MarkupCache(WicketMarkupRemover wicketMarkupRemover) {
        this(wicketMarkupRemover, () -> false);
    }

    MarkupCache(WicketMarkupRemover wicketMarkupRemover, BooleanSupplier wicketMarkupCacheClearer) {
        this.wicketMarkupRemover = Objects.requireNonNull(wicketMarkupRemover, "wicketMarkupRemover");
        this.wicketMarkupCacheClearer = Objects.requireNonNull(wicketMarkupCacheClearer, "wicketMarkupCacheClearer");
    }

    /**
     * Returns the {@link GeneratedMarkup} instance for given container. The container must implement {@link
     * MarkupSourceProvider}. Markup is regenerated only when explicitly invalidated.
     *
     * @param container
     * @return
     */
    public GeneratedMarkup getMarkup(IGenericComponent<BrixNode> container) {
        if (!(container instanceof MarkupSourceProvider)) {
            throw new IllegalArgumentException("Argument 'container' must implement MarkupSourceProvider");
        }
        MarkupSourceProvider provider = (MarkupSourceProvider) container;
        BrixNode node = container.getModelObject();
        String workspace = node.getSession().getWorkspace().getName();
        ConcurrentMap<CacheKey, GeneratedMarkup> cache = getWorkspaceCache(workspace);
        CacheKey key = getKey(container, node);
        return cache.computeIfAbsent(key,
                ignored -> new GeneratedMarkup(provider.getMarkupSource(), workspace, key.nodeId));
    }

    public void invalidate(BrixNode node) {
        if (node == null) {
            return;
        }
        String workspace = node.getSession().getWorkspace().getName();
        String nodeId = getNodeId(node);
        invalidate(workspace, nodeId);
    }

    public void invalidate(String workspace, String nodeId) {
        if (workspace == null || nodeId == null) {
            return;
        }
        ConcurrentMap<CacheKey, GeneratedMarkup> cache = workspaceCaches.get(workspace);
        if (cache != null) {
            cache.keySet().removeIf(key -> nodeId.equals(key.nodeId));
        }
        retireWicketCacheKeys(workspace, nodeId);
    }

    /**
     * Detaches all generated markup for a workspace. Use this after replacing workspace content through a JCR clone
     * or XML import, because those operations do not emit the node save events used for regular invalidation. Markup
     * generation already in progress can only populate the detached cache and is therefore not visible to later
     * requests.
     *
     * @param workspace workspace whose markup should be discarded
     */
    public void invalidateWorkspace(String workspace) {
        if (workspace == null) {
            return;
        }
        workspaceCaches.remove(workspace);
        ConcurrentMap<WicketCacheIdentity, String> removedWicketKeys = wicketCacheKeys.remove(workspace);
        if (removedWicketKeys != null) {
            for (String wicketCacheKey : removedWicketKeys.values()) {
                retireWicketCacheKey(wicketCacheKey);
            }
        }
    }

    private ConcurrentMap<CacheKey, GeneratedMarkup> getWorkspaceCache(String workspace) {
        return workspaceCaches.computeIfAbsent(workspace,
                ignored -> new ConcurrentHashMap<CacheKey, GeneratedMarkup>());
    }

    String getWicketCacheKey(String workspace, String nodeId, String componentClass, String locale, String style,
                             String variation, String markupType, String markupHash) {
        drainRetiredWicketCacheKeys();

        WicketCacheIdentity identity = new WicketCacheIdentity(nodeId, componentClass, locale, style, variation,
                markupType);
        String currentKey = identity.toWicketCacheKey(workspace, markupHash);
        ConcurrentMap<WicketCacheIdentity, String> workspaceKeys = wicketCacheKeys.computeIfAbsent(workspace,
                ignored -> new ConcurrentHashMap<WicketCacheIdentity, String>());
        String previousKey = workspaceKeys.put(identity, currentKey);
        if (previousKey != null && !previousKey.equals(currentKey)) {
            retireWicketCacheKey(previousKey);
        }

        if (wicketCacheKeys.get(workspace) != workspaceKeys && workspaceKeys.remove(identity, currentKey)) {
            // The workspace was invalidated while this request calculated its key. Its content hash keeps the
            // request correct; retire the detached key on the next request so it cannot accumulate in Wicket.
            retireWicketCacheKey(currentKey);
        }
        if (retiredWicketCacheKeysSinceClear.get() >= WICKET_CACHE_CLEAR_THRESHOLD) {
            drainRetiredWicketCacheKeys();
        }
        return currentKey;
    }

    private void retireWicketCacheKeys(String workspace, String nodeId) {
        ConcurrentMap<WicketCacheIdentity, String> workspaceKeys = wicketCacheKeys.get(workspace);
        if (workspaceKeys == null) {
            return;
        }
        for (ConcurrentMap.Entry<WicketCacheIdentity, String> entry : workspaceKeys.entrySet()) {
            WicketCacheIdentity identity = entry.getKey();
            String wicketCacheKey = entry.getValue();
            if (nodeId.equals(identity.nodeId) && workspaceKeys.remove(identity, wicketCacheKey)) {
                retireWicketCacheKey(wicketCacheKey);
            }
        }
    }

    private void retireWicketCacheKey(String cacheKey) {
        retiredWicketCacheKeys.add(cacheKey);
        retiredWicketCacheKeysSinceClear.incrementAndGet();
    }

    private void drainRetiredWicketCacheKeys() {
        if (!drainingRetiredWicketCacheKeys.compareAndSet(false, true)) {
            return;
        }
        try {
            long retiredSinceClear = retiredWicketCacheKeysSinceClear.get();
            if (retiredSinceClear >= WICKET_CACHE_CLEAR_THRESHOLD && wicketMarkupCacheClearer.getAsBoolean()) {
                // Preserve retirements that raced with the clear. Over-counting keys retired during the clear is safe
                // and merely causes the next preventive clear to happen a little earlier.
                retiredWicketCacheKeysSinceClear.addAndGet(-retiredSinceClear);
                log.warn("Cleared Wicket markup cache after {} retired Brix markup keys to prevent unbounded "
                        + "revision-key growth", retiredSinceClear);
            }

            String cacheKey = retiredWicketCacheKeys.peek();
            while (cacheKey != null && wicketMarkupRemover.remove(cacheKey)) {
                retiredWicketCacheKeys.poll();
                cacheKey = retiredWicketCacheKeys.peek();
            }
        } finally {
            drainingRetiredWicketCacheKeys.set(false);
        }
    }

    private static boolean removeWicketMarkup(String cacheKey) {
        if (!Application.exists()) {
            return false;
        }
        try {
            org.apache.wicket.markup.MarkupCache.get().removeMarkup(cacheKey);
            return true;
        } catch (RuntimeException e) {
            // Cache cleanup is best effort and must never fail page rendering. A later request retries the key.
            return false;
        }
    }

    private static boolean clearWicketMarkupCache() {
        if (!Application.exists()) {
            return false;
        }
        try {
            org.apache.wicket.markup.MarkupCache.get().clear();
            return true;
        } catch (RuntimeException e) {
            // A later request retries the preventive clear.
            return false;
        }
    }

    /**
     * Returns the cache key for the given container within its workspace bucket.
     *
     * @param container
     * @param node
     * @return
     */
    private CacheKey getKey(IGenericComponent<BrixNode> container, BrixNode node) {
        String nodeId = "";
        if (node != null) {
            nodeId = getNodeId(node);
        }
        return new CacheKey(container.getClass().getName(), nodeId);
    }

    static String getNodeId(BrixNode node) {
        if (node.isNodeType("mix:referenceable")) {
            return node.getIdentifier();
        }
        return node.getPath();
    }

    @FunctionalInterface
    interface WicketMarkupRemover {
        boolean remove(String cacheKey);
    }

    private static class WicketCacheIdentity {
        private final String nodeId;
        private final String componentClass;
        private final String locale;
        private final String style;
        private final String variation;
        private final String markupType;

        private WicketCacheIdentity(String nodeId, String componentClass, String locale, String style,
                                    String variation, String markupType) {
            this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
            this.componentClass = Objects.requireNonNull(componentClass, "componentClass");
            this.locale = locale;
            this.style = style;
            this.variation = variation;
            this.markupType = markupType;
        }

        private String toWicketCacheKey(String workspace, String markupHash) {
            StringBuilder key = new StringBuilder("brix-markup:v1:");
            appendPart(key, workspace);
            appendPart(key, nodeId);
            appendPart(key, componentClass);
            appendPart(key, locale);
            appendPart(key, style);
            appendPart(key, variation);
            appendPart(key, markupType);
            appendPart(key, markupHash);
            return key.toString();
        }

        private static void appendPart(StringBuilder target, String value) {
            if (value == null) {
                target.append("-:");
            } else {
                target.append(value.length()).append(':').append(value).append(':');
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof WicketCacheIdentity)) {
                return false;
            }
            WicketCacheIdentity identity = (WicketCacheIdentity) other;
            return Objects.equals(nodeId, identity.nodeId)
                    && Objects.equals(componentClass, identity.componentClass)
                    && Objects.equals(locale, identity.locale)
                    && Objects.equals(style, identity.style)
                    && Objects.equals(variation, identity.variation)
                    && Objects.equals(markupType, identity.markupType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeId, componentClass, locale, style, variation, markupType);
        }
    }

    private static class CacheKey {
        private final String componentClass;
        private final String nodeId;

        private CacheKey(String componentClass, String nodeId) {
            this.componentClass = componentClass;
            this.nodeId = nodeId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey key = (CacheKey) other;
            return Objects.equals(componentClass, key.componentClass)
                    && Objects.equals(nodeId, key.nodeId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(componentClass, nodeId);
        }
    }
}
