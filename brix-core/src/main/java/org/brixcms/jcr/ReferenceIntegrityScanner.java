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

package org.brixcms.jcr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.jcr.PropertyType;

import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrNodeIterator;
import org.brixcms.jcr.api.JcrProperty;
import org.brixcms.jcr.api.JcrPropertyIterator;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.jcr.api.JcrValue;

/**
 * Scans a JCR subtree for broken references and optional UUID-like string references.
 */
public final class ReferenceIntegrityScanner {
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    public ScanResult scan(JcrNode root) {
        return scan(root, new Options());
    }

    public ScanResult scan(JcrNode root, Options options) {
        if (root == null) {
            throw new IllegalArgumentException("Argument 'root' may not be null.");
        }
        if (options == null) {
            options = new Options();
        }

        List<ReferenceEntry> entries = new ArrayList<ReferenceEntry>();
        Map<String, Boolean> existenceCache = new HashMap<String, Boolean>();
        scanNode(root, options, entries, existenceCache);

        Collections.sort(entries, new Comparator<ReferenceEntry>() {
            @Override
            public int compare(ReferenceEntry left, ReferenceEntry right) {
                int byPath = left.getSourceNodePath().compareTo(right.getSourceNodePath());
                if (byPath != 0) {
                    return byPath;
                }
                int byProperty = left.getPropertyName().compareTo(right.getPropertyName());
                if (byProperty != 0) {
                    return byProperty;
                }
                int byIdentifier = left.getTargetIdentifier().compareTo(right.getTargetIdentifier());
                if (byIdentifier != 0) {
                    return byIdentifier;
                }
                return left.getKind().compareTo(right.getKind());
            }
        });
        return new ScanResult(entries);
    }

    private void scanNode(JcrNode node, Options options, List<ReferenceEntry> entries, Map<String, Boolean> existenceCache) {
        String sourcePath = node.getPath();
        JcrPropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            JcrProperty property = properties.nextProperty();
            scanProperty(node.getSession(), sourcePath, property, options, entries, existenceCache);
        }

        JcrNodeIterator children = node.getNodes();
        while (children.hasNext()) {
            scanNode(children.nextNode(), options, entries, existenceCache);
        }
    }

    private void scanProperty(JcrSession session, String sourcePath, JcrProperty property, Options options,
                              List<ReferenceEntry> entries, Map<String, Boolean> existenceCache) {
        int type = property.getType();
        String propertyName = property.getName();

        if (type == PropertyType.REFERENCE || type == PropertyType.WEAKREFERENCE) {
            scanReferenceProperty(session, sourcePath, propertyName, property, type, entries, existenceCache);
        } else if (options.isIncludeStringUuidCandidates() && type == PropertyType.STRING
                && shouldScanStringProperty(propertyName, options)) {
            scanStringUuidCandidates(session, sourcePath, propertyName, property, entries, existenceCache);
        }
    }

    private void scanReferenceProperty(JcrSession session, String sourcePath, String propertyName, JcrProperty property,
                                       int type, List<ReferenceEntry> entries, Map<String, Boolean> existenceCache) {
        if (property.getDefinition().isMultiple()) {
            JcrValue[] values = property.getValues();
            for (JcrValue value : values) {
                addEntry(session, sourcePath, propertyName, value.getString(), kindFor(type), entries, existenceCache);
            }
        } else {
            addEntry(session, sourcePath, propertyName, property.getValue().getString(), kindFor(type), entries, existenceCache);
        }
    }

    private void scanStringUuidCandidates(JcrSession session, String sourcePath, String propertyName, JcrProperty property,
                                          List<ReferenceEntry> entries, Map<String, Boolean> existenceCache) {
        if (property.getDefinition().isMultiple()) {
            JcrValue[] values = property.getValues();
            for (JcrValue value : values) {
                String candidate = value.getString();
                if (looksLikeUuid(candidate)) {
                    addEntry(session, sourcePath, propertyName, candidate, ReferenceKind.STRING_UUID, entries, existenceCache);
                }
            }
        } else {
            String candidate = property.getString();
            if (looksLikeUuid(candidate)) {
                addEntry(session, sourcePath, propertyName, candidate, ReferenceKind.STRING_UUID, entries, existenceCache);
            }
        }
    }

    private void addEntry(JcrSession session, String sourcePath, String propertyName, String identifier, ReferenceKind kind,
                          List<ReferenceEntry> entries, Map<String, Boolean> existenceCache) {
        boolean exists = targetExists(session, identifier, existenceCache);
        entries.add(new ReferenceEntry(sourcePath, propertyName, identifier, exists, kind));
    }

    private boolean targetExists(JcrSession session, String identifier, Map<String, Boolean> existenceCache) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        Boolean cached = existenceCache.get(identifier);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean exists = JcrUtil.getNodeByUUID(session, identifier) != null;
        existenceCache.put(identifier, Boolean.valueOf(exists));
        return exists;
    }

    private boolean shouldScanStringProperty(String propertyName, Options options) {
        Set<String> names = options.getStringReferencePropertyNames();
        if (names.isEmpty()) {
            return true;
        }
        return names.contains(propertyName.toLowerCase(Locale.ROOT));
    }

    private static boolean looksLikeUuid(String value) {
        return value != null && UUID_PATTERN.matcher(value).matches();
    }

    private static ReferenceKind kindFor(int type) {
        if (type == PropertyType.WEAKREFERENCE) {
            return ReferenceKind.WEAKREFERENCE;
        }
        return ReferenceKind.REFERENCE;
    }

    public static final class Options {
        private boolean includeStringUuidCandidates;
        private final Set<String> stringReferencePropertyNames = new HashSet<String>();

        public boolean isIncludeStringUuidCandidates() {
            return includeStringUuidCandidates;
        }

        public Options setIncludeStringUuidCandidates(boolean includeStringUuidCandidates) {
            this.includeStringUuidCandidates = includeStringUuidCandidates;
            return this;
        }

        public Set<String> getStringReferencePropertyNames() {
            return stringReferencePropertyNames;
        }

        public Options addStringReferencePropertyName(String propertyName) {
            if (propertyName == null || propertyName.trim().isEmpty()) {
                return this;
            }
            stringReferencePropertyNames.add(propertyName.trim().toLowerCase(Locale.ROOT));
            return this;
        }
    }

    public enum ReferenceKind {
        REFERENCE,
        WEAKREFERENCE,
        STRING_UUID
    }

    public static final class ReferenceEntry {
        private final String sourceNodePath;
        private final String propertyName;
        private final String targetIdentifier;
        private final boolean exists;
        private final ReferenceKind kind;

        public ReferenceEntry(String sourceNodePath, String propertyName, String targetIdentifier,
                              boolean exists, ReferenceKind kind) {
            this.sourceNodePath = sourceNodePath;
            this.propertyName = propertyName;
            this.targetIdentifier = targetIdentifier != null ? targetIdentifier : "";
            this.exists = exists;
            this.kind = kind;
        }

        public String getSourceNodePath() {
            return sourceNodePath;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getTargetIdentifier() {
            return targetIdentifier;
        }

        public boolean isExists() {
            return exists;
        }

        public ReferenceKind getKind() {
            return kind;
        }
    }

    public static final class ScanResult {
        private final List<ReferenceEntry> entries;

        public ScanResult(List<ReferenceEntry> entries) {
            this.entries = Collections.unmodifiableList(new ArrayList<ReferenceEntry>(entries));
        }

        public List<ReferenceEntry> getEntries() {
            return entries;
        }

        public int getTotalCount() {
            return entries.size();
        }

        public int getMissingCount() {
            int missing = 0;
            for (ReferenceEntry entry : entries) {
                if (!entry.isExists()) {
                    missing++;
                }
            }
            return missing;
        }

        public Map<String, Integer> getMissingByTargetIdentifier() {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (ReferenceEntry entry : entries) {
                if (!entry.isExists()) {
                    increment(counts, entry.getTargetIdentifier());
                }
            }
            return sortByCountDescending(counts);
        }

        public Map<String, Integer> getMissingByPropertyName() {
            Map<String, Integer> counts = new HashMap<String, Integer>();
            for (ReferenceEntry entry : entries) {
                if (!entry.isExists()) {
                    increment(counts, entry.getPropertyName());
                }
            }
            return sortByCountDescending(counts);
        }

        private void increment(Map<String, Integer> counts, String key) {
            Integer current = counts.get(key);
            counts.put(key, current == null ? 1 : current + 1);
        }

        private Map<String, Integer> sortByCountDescending(Map<String, Integer> counts) {
            List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                    int byCount = right.getValue().compareTo(left.getValue());
                    if (byCount != 0) {
                        return byCount;
                    }
                    return left.getKey().compareTo(right.getKey());
                }
            });
            Map<String, Integer> sorted = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Integer> entry : entries) {
                sorted.put(entry.getKey(), entry.getValue());
            }
            return sorted;
        }
    }
}
