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

package org.brixcms.plugin.site.page.tile;

import org.apache.wicket.Component;
import org.apache.wicket.MetaDataKey;
import org.apache.wicket.model.IModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.brixcms.BrixNodeModel;
import org.brixcms.jcr.wrapper.BrixNode;
import org.brixcms.markup.tag.ComponentTag;
import org.brixcms.markup.tag.simple.SimpleTag;
import org.brixcms.markup.variable.VariableKeyProvider;
import org.brixcms.plugin.site.page.AbstractContainer;
import org.brixcms.plugin.site.SitePlugin;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for tags that represent {@link Tile}s
 *
 * @author ivaynberg, Matej Knopp
 */
public class TileTag extends SimpleTag
        implements
        ComponentTag,
        VariableKeyProvider {
    private final static AtomicLong atomicLong = new AtomicLong();

    private final static String PREFIX = "tile-";
    /**
     * name of tile this tag is attached to
     */
    private final String tileName;

    private final BrixNodeModel tileContainerNodeModel;
    private String tileContainerKey;

    private String id;

    /**
     * Constructor
     *
     * @param name
     * @param type
     * @param attributeMap
     * @param tileName
     */
    public TileTag(String name, Type type, Map<String, String> attributeMap, AbstractContainer tileContainerNode, String tileName) {
        super(name, type, attributeMap);
        this.tileName = tileName;
        tileContainerNodeModel = new BrixNodeModel(tileContainerNode);
        tileContainerNodeModel.detach();
    }

    /**
     * @return name of tile
     */
    public String getTileName() {
        return tileName;
    }


    /**
     * {@inheritDoc}
     */
    public Component getComponent(String id, IModel<BrixNode> pageNodeModel) {
        AbstractContainer container = getTileContainer();
        if(container == null) {
            return null;
        }
        BrixNode tileNode = getTileNode(container, tileName);

        if (tileNode != null) {
            Tile tile = Tile.Helper.getTileOfType(TileContainerFacet.getTileClassName(tileNode),
                    tileNode.getBrix());
            return tile.newViewer(id, new BrixNodeModel(tileNode));
        } else {
            return null;
        }
    }

    /**
     * return unique id of this tag
     */
    public String getUniqueTagId() {
        if (id == null) {
            id = PREFIX + atomicLong.incrementAndGet();
        }
        return id;
    }


    /**
     * {@inheritDoc}
     */
    public Collection<String> getVariableKeys() {
        AbstractContainer container = getTileContainer();
        if (container == null) {
            return null;
        }
        BrixNode tileNode = getTileNode(container, tileName);
        if (tileNode != null) {
            Tile tile = Tile.Helper.getTileOfType(TileContainerFacet.getTileClassName(tileNode),
                    tileNode.getBrix());
            if (tile instanceof VariableKeyProvider provider) {
                return provider.getVariableKeys();
            }
        }
        return null;
    }

    /**
     * @return tile container that contains the tile
     */
    protected AbstractContainer getTileContainer() {
        RequestCycle cycle = RequestCycle.get();
        String containerKey = getTileContainerKey();
        if (cycle != null && containerKey != null) {
            Map<String, AbstractContainer> cache = cycle.getMetaData(CONTAINER_CACHE_KEY);
            if (cache == null) {
                cache = new HashMap<String, AbstractContainer>();
                cycle.setMetaData(CONTAINER_CACHE_KEY, cache);
            }
            AbstractContainer cached = cache.get(containerKey);
            if (cached != null) {
                return cached;
            }
        }
        AbstractContainer container = (AbstractContainer) tileContainerNodeModel.getObject();
        tileContainerNodeModel.detach();
        if (cycle != null && containerKey != null) {
            Map<String, AbstractContainer> cache = cycle.getMetaData(CONTAINER_CACHE_KEY);
            if (cache != null) {
                cache.put(containerKey, container);
            }
        }
        return container;
    }

    private BrixNode getTileNode(AbstractContainer container, String id) {
        Map<String, BrixNode> tileMap = getTileMap(container);
        if (tileMap != null) {
            return tileMap.get(id);
        }
        return container.getTileNode(id);
    }

    private Map<String, BrixNode> getTileMap(AbstractContainer container) {
        RequestCycle cycle = RequestCycle.get();
        String containerKey = getTileContainerKey();
        if (cycle == null || containerKey == null) {
            return null;
        }
        Map<String, Map<String, BrixNode>> cache = cycle.getMetaData(TILE_CACHE_KEY);
        if (cache == null) {
            cache = new HashMap<String, Map<String, BrixNode>>();
            cycle.setMetaData(TILE_CACHE_KEY, cache);
        }
        Map<String, BrixNode> tileMap = cache.get(containerKey);
        if (tileMap == null) {
            tileMap = buildTileMap(container);
            cache.put(containerKey, tileMap);
        }
        return tileMap;
    }

    private Map<String, BrixNode> buildTileMap(AbstractContainer container) {
        Map<String, BrixNode> result = new HashMap<String, BrixNode>();
        AbstractContainer current = container;
        while (current != null) {
            addTiles(current, result);
            current = current.getTemplate();
        }
        AbstractContainer global = SitePlugin.get().getGlobalContainer(container.getSession());
        if (global != null) {
            addTiles(global, result);
        }
        return result;
    }

    private void addTiles(AbstractContainer container, Map<String, BrixNode> result) {
        List<BrixNode> tiles = container.tiles().getTileNodes();
        for (BrixNode node : tiles) {
            String id = TileContainerFacet.getTileId(node);
            if (id != null && !result.containsKey(id)) {
                result.put(id, node);
            }
        }
    }

    private String getTileContainerKey() {
        if (tileContainerKey == null) {
            tileContainerKey = tileContainerNodeModel.getCacheKey();
        }
        return tileContainerKey;
    }

    private static final MetaDataKey<Map<String, AbstractContainer>> CONTAINER_CACHE_KEY =
            new MetaDataKey<Map<String, AbstractContainer>>() {
            };
    private static final MetaDataKey<Map<String, Map<String, BrixNode>>> TILE_CACHE_KEY =
            new MetaDataKey<Map<String, Map<String, BrixNode>>>() {
            };
}
