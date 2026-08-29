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

import org.brixcms.markup.tag.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains list of generated markup items and expiration token.
 * <p>
 * TODO: Consider optimizing the list of items by grouping static items together (as text).
 *
 * @author Matej Knopp
 */
class GeneratedMarkup {
    final List<Item> items;

    final Object expirationToken;

    final String doctype;

    final String workspace;

    final String nodeId;

    /**
     * Creates new {@link GeneratedMarkup} instance from given {@link MarkupSource}.
     *
     * @param markupSource
     * @param workspace workspace used to cache the generated markup
     * @param nodeId stable node identity used to cache the generated markup
     */
    public GeneratedMarkup(MarkupSource markupSource, String workspace, String nodeId) {
        if (markupSource == null) {
            throw new IllegalArgumentException("Argument 'markupSource' may not be null.");
        }
        this.workspace = workspace;
        this.nodeId = nodeId;
        this.expirationToken = markupSource.getExpirationToken();
        List<Item> generatedItems = new ArrayList<Item>();
        Item item = markupSource.nextMarkupItem();
        while (item != null) {
            generatedItems.add(item);
            item = markupSource.nextMarkupItem();
        }
        items = List.copyOf(generatedItems);
        this.doctype = markupSource.getDoctype();
    }
}
