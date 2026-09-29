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

package org.brixcms.plugin.site;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.brixcms.Brix;
import org.brixcms.auth.AuthorizationStrategy;
import org.brixcms.config.BrixConfig;
import org.brixcms.plugin.site.fallback.FallbackNodePlugin;
import org.brixcms.registry.ExtensionPoint;
import org.brixcms.registry.ExtensionPointRegistry;
import org.junit.Before;
import org.junit.Test;

public class SitePluginCacheTest {
    private TestRegistry registry;
    private SitePlugin site;

    @Before
    public void setUp() {
        registry = new TestRegistry();
        Brix brix = new Brix(new BrixConfig(null, null, null) {
            @Override
            public ExtensionPointRegistry getRegistry() {
                return registry;
            }
        }) {
            @Override
            public AuthorizationStrategy newAuthorizationStrategy() {
                return null;
            }
        };
        site = SitePlugin.get(brix);
    }

    @Test
    public void preservesOrderFirstMatchNullTypesAndFallback() {
        SiteNodePlugin fallback = site.getNodePluginForType("unknown");
        assertTrue(fallback instanceof FallbackNodePlugin);
        assertSame(fallback, site.getNodePluginForType(null));
        assertTrue(site.getNodePlugins().isEmpty());

        SiteNodePlugin first = new TestNodePlugin("product");
        SiteNodePlugin duplicate = new TestNodePlugin("product");
        SiteNodePlugin nullType = new TestNodePlugin(null);
        registry.registerNodePlugin(first);
        registry.registerNodePlugin(duplicate);
        registry.registerNodePlugin(nullType);

        Collection<SiteNodePlugin> plugins = site.getNodePlugins();
        assertEquals(List.of(first, duplicate, nullType), new ArrayList<>(plugins));
        assertSame(plugins, site.getNodePlugins());
        assertSame(first, site.getNodePluginForType("product"));
        assertSame(nullType, site.getNodePluginForType(null));
        assertSame(fallback, site.getNodePluginForType("unknown"));
        assertThrows(UnsupportedOperationException.class, plugins::clear);
    }

    @Test
    public void registryEventsInvalidateBothViewsAndPreserveExistingSnapshots() {
        SiteNodePlugin first = new TestNodePlugin("product");
        SiteNodePlugin second = new TestNodePlugin("product");
        registry.registerNodePlugin(first);
        Collection<SiteNodePlugin> original = site.getNodePlugins();
        assertSame(first, site.getNodePluginForType("product"));

        registry.registerNodePlugin(second);
        Collection<SiteNodePlugin> registered = site.getNodePlugins();
        assertNotSame(original, registered);
        assertEquals(List.of(first, second), new ArrayList<>(registered));
        assertSame(first, site.getNodePluginForType("product"));

        registry.unregisterNodePlugin(first);
        assertSame(second, site.getNodePluginForType("product"));
        assertEquals(List.of(second), new ArrayList<>(site.getNodePlugins()));
        assertEquals(List.of(first), new ArrayList<>(original));
        assertEquals(List.of(first, second), new ArrayList<>(registered));

        registry.unregisterNodePlugin(second);
        assertTrue(site.getNodePlugins().isEmpty());
        assertSame(site.getNodePluginForType("unknown"), site.getNodePluginForType("product"));
    }

    @Test
    public void concurrentLookupWaitsForTheCompleteTypeMap() throws Exception {
        CountDownLatch buildingMap = new CountDownLatch(1);
        CountDownLatch continueBuilding = new CountDownLatch(1);
        SiteNodePlugin plugin = new TestNodePlugin("product") {
            @Override
            public String getNodeType() {
                buildingMap.countDown();
                await(continueBuilding);
                return super.getNodeType();
            }
        };
        registry.registerNodePlugin(plugin);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Collection<SiteNodePlugin>> builder = executor.submit(site::getNodePlugins);
            await(buildingMap);
            CountDownLatch lookupStarted = new CountDownLatch(1);
            Future<SiteNodePlugin> reader = executor.submit(() -> {
                lookupStarted.countDown();
                return site.getNodePluginForType("product");
            });
            await(lookupStarted);

            // The old cache published its list before reaching the paused getNodeType(),
            // allowing this lookup to dereference a null type map.
            assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS));
            continueBuilding.countDown();
            assertSame(plugin, reader.get(5, TimeUnit.SECONDS));
            assertSame(builder.get(5, TimeUnit.SECONDS), site.getNodePlugins());
        } finally {
            continueBuilding.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void failedBuildDoesNotPublishAPartialCache() {
        AtomicInteger attempts = new AtomicInteger();
        SiteNodePlugin plugin = new TestNodePlugin("product") {
            @Override
            public String getNodeType() {
                if (attempts.getAndIncrement() == 0) {
                    throw new IllegalStateException("type lookup failed");
                }
                return super.getNodeType();
            }
        };
        registry.registerNodePlugin(plugin);

        assertThrows(IllegalStateException.class, site::getNodePlugins);
        assertSame(plugin, site.getNodePluginForType("product"));
        assertEquals(2, attempts.get());
    }

    @Test
    public void concurrentReadsRemainValidDuringRepeatedInvalidation() throws Exception {
        SiteNodePlugin stable = new TestNodePlugin("product");
        SiteNodePlugin changing = new TestNodePlugin("temporary");
        registry.registerNodePlugin(stable);
        SiteNodePlugin fallback = site.getNodePluginForType("unknown");
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        try {
            for (int reader = 0; reader < 4; reader++) {
                tasks.add(executor.submit(() -> {
                    await(start);
                    for (int i = 0; i < 10_000; i++) {
                        assertSame(stable, site.getNodePluginForType("product"));
                        assertSame(fallback, site.getNodePluginForType("unknown"));
                        assertTrue(site.getNodePlugins().contains(stable));
                        SiteNodePlugin temporary = site.getNodePluginForType("temporary");
                        assertTrue(temporary == changing || temporary == fallback);
                    }
                }));
            }
            tasks.add(executor.submit(() -> {
                await(start);
                for (int i = 0; i < 2_000; i++) {
                    registry.registerNodePlugin(changing);
                    registry.unregisterNodePlugin(changing);
                }
            }));
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertSame(fallback, site.getNodePluginForType("temporary"));
            assertFalse(site.getNodePlugins().contains(changing));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue("Timed out waiting for the test thread", latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static class TestNodePlugin extends FallbackNodePlugin {
        private final String type;

        private TestNodePlugin(String type) {
            this.type = type;
        }

        @Override
        public String getNodeType() {
            return type;
        }
    }

    /**
     * Supplies immutable registry snapshots and explicitly emits registry events. This isolates
     * the SitePlugin cache tests from the registry's mutable collections and event dispatch.
     */
    private static class TestRegistry extends ExtensionPointRegistry {
        private volatile List<SiteNodePlugin> plugins = List.of();
        private final List<Listener> listeners = new ArrayList<>();

        @Override
        public void register(Listener listener, boolean replay) {
            super.register(listener, replay);
            listeners.add(listener);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Collection<T> lookupCollection(ExtensionPoint<T> point) {
            if (point == SiteNodePlugin.POINT) {
                return (Collection<T>) plugins;
            }
            return super.lookupCollection(point);
        }

        private void registerNodePlugin(SiteNodePlugin plugin) {
            List<SiteNodePlugin> updated = new ArrayList<>(plugins);
            updated.add(plugin);
            plugins = List.copyOf(updated);
            for (Listener listener : listeners) {
                listener.registered(SiteNodePlugin.POINT, plugin);
            }
        }

        private void unregisterNodePlugin(SiteNodePlugin plugin) {
            List<SiteNodePlugin> updated = new ArrayList<>(plugins);
            updated.remove(plugin);
            plugins = List.copyOf(updated);
            for (Listener listener : listeners) {
                listener.unregistered(SiteNodePlugin.POINT, plugin);
            }
        }
    }
}
