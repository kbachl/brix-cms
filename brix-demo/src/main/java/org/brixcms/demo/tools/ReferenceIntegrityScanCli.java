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

package org.brixcms.demo.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.jcr.Repository;

import org.apache.jackrabbit.core.RepositoryImpl;
import org.brixcms.demo.ApplicationProperties;
import org.brixcms.jcr.ReferenceIntegrityScanner;
import org.brixcms.jcr.ReferenceIntegrityScanner.Options;
import org.brixcms.jcr.ReferenceIntegrityScanner.ReferenceEntry;
import org.brixcms.jcr.ReferenceIntegrityScanner.ScanResult;
import org.brixcms.jcr.ThreadLocalSessionFactory;
import org.brixcms.jcr.api.JcrNode;
import org.brixcms.jcr.api.JcrSession;
import org.brixcms.util.JcrUtils;

/**
 * CLI scanner for broken JCR references.
 */
public final class ReferenceIntegrityScanCli {
    private static final int TOP_N = 20;

    private ReferenceIntegrityScanCli() {
    }

    public static void main(String[] args) {
        CliOptions options;
        try {
            options = CliOptions.parse(args);
            if (options.help) {
                printUsage();
                return;
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsage();
            System.exit(2);
            return;
        }

        int exitCode = run(options);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int run(CliOptions options) {
        ApplicationProperties properties = new ApplicationProperties(options.prefix);
        Repository repository = null;
        ThreadLocalSessionFactory sessionFactory = null;
        try {
            repository = JcrUtils.createRepository(properties.getJcrRepositoryUrl());
            sessionFactory = new ThreadLocalSessionFactory(repository, properties.buildSimpleCredentials());
            String workspace = options.workspace != null ? options.workspace : properties.getJcrDefaultWorkspace();

            JcrSession session = JcrSession.Wrapper.wrap(sessionFactory.getCurrentSession(workspace));
            if (!session.nodeExists(options.path)) {
                System.err.println("Path does not exist: " + options.path);
                return 2;
            }

            JcrNode root = session.getNode(options.path);
            ReferenceIntegrityScanner scanner = new ReferenceIntegrityScanner();
            Options scannerOptions = new Options()
                    .setIncludeStringUuidCandidates(options.includeStringUuidCandidates);
            for (String field : options.stringFields.split(",")) {
                scannerOptions.addStringReferencePropertyName(field);
            }

            ScanResult result = scanner.scan(root, scannerOptions);
            String report = "json".equals(options.format) ? toJson(result) : toCsv(result);
            String summary = buildSummary(result);

            if (options.output != null) {
                Path target = Path.of(options.output);
                Files.write(target, report.getBytes(StandardCharsets.UTF_8));
                System.out.println(summary);
                System.out.println("Report written to: " + target.toAbsolutePath());
            } else {
                System.out.println(report);
                System.err.println(summary);
            }

            return 0;
        } catch (Exception e) {
            System.err.println("Reference scan failed: " + e.getMessage());
            e.printStackTrace(System.err);
            return 1;
        } finally {
            if (sessionFactory != null) {
                sessionFactory.cleanup();
            }
            if (repository instanceof RepositoryImpl impl) {
                impl.shutdown();
            }
        }
    }

    private static String buildSummary(ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Summary\n");
        sb.append("-------\n");
        sb.append("totalReferences=").append(result.getTotalCount()).append('\n');
        sb.append("missingReferences=").append(result.getMissingCount()).append('\n');
        sb.append("topMissingTargetIdentifiers:\n");
        appendTopCounts(sb, result.getMissingByTargetIdentifier());
        sb.append("topMissingProperties:\n");
        appendTopCounts(sb, result.getMissingByPropertyName());
        return sb.toString();
    }

    private static void appendTopCounts(StringBuilder sb, Map<String, Integer> counts) {
        int index = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (index >= TOP_N) {
                break;
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
            index++;
        }
        if (index == 0) {
            sb.append("(none)\n");
        }
    }

    private static String toCsv(ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("sourceNodePath,propertyName,targetIdentifier,exists,kind\n");
        for (ReferenceEntry entry : result.getEntries()) {
            sb.append(csv(entry.getSourceNodePath())).append(',');
            sb.append(csv(entry.getPropertyName())).append(',');
            sb.append(csv(entry.getTargetIdentifier())).append(',');
            sb.append(entry.isExists()).append(',');
            sb.append(entry.getKind().name()).append('\n');
        }
        return sb.toString();
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return quote ? "\"" + escaped + "\"" : escaped;
    }

    private static String toJson(ScanResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"summary\": {\n");
        sb.append("    \"totalReferences\": ").append(result.getTotalCount()).append(",\n");
        sb.append("    \"missingReferences\": ").append(result.getMissingCount()).append(",\n");
        sb.append("    \"missingByTargetIdentifier\": ").append(jsonMap(result.getMissingByTargetIdentifier())).append(",\n");
        sb.append("    \"missingByPropertyName\": ").append(jsonMap(result.getMissingByPropertyName())).append('\n');
        sb.append("  },\n");
        sb.append("  \"entries\": [\n");
        for (int i = 0; i < result.getEntries().size(); i++) {
            ReferenceEntry entry = result.getEntries().get(i);
            sb.append("    {");
            sb.append("\"sourceNodePath\": ").append(json(entry.getSourceNodePath())).append(", ");
            sb.append("\"propertyName\": ").append(json(entry.getPropertyName())).append(", ");
            sb.append("\"targetIdentifier\": ").append(json(entry.getTargetIdentifier())).append(", ");
            sb.append("\"exists\": ").append(entry.isExists()).append(", ");
            sb.append("\"kind\": ").append(json(entry.getKind().name()));
            sb.append("}");
            if (i < result.getEntries().size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jsonMap(Map<String, Integer> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int index = 0;
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append(json(entry.getKey())).append(": ").append(entry.getValue());
            index++;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    private static void printUsage() {
        System.out.println("Usage: ReferenceIntegrityScanCli [options]");
        System.out.println("  --workspace <name>      Workspace name (default: application property)");
        System.out.println("  --path <absPath>        Root path to scan (default: /)");
        System.out.println("  --format <csv|json>     Output format (default: csv)");
        System.out.println("  --output <file>         Output file (default: stdout)");
        System.out.println("  --prefix <prefix>       Application property prefix (default: brix.demo)");
        System.out.println("  --include-string-uuid   Also scan STRING values that look like UUID");
        System.out.println("  --string-fields <list>  Comma-separated property names for STRING UUID scan");
        System.out.println("  --help                  Show usage");
    }

    private static final class CliOptions {
        private String workspace;
        private String path = "/";
        private String format = "csv";
        private String output;
        private String prefix = "brix.demo";
        private boolean includeStringUuidCandidates;
        private String stringFields = "";
        private boolean help;

        static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--workspace".equals(arg) && i + 1 < args.length) {
                    options.workspace = args[++i];
                } else if ("--path".equals(arg) && i + 1 < args.length) {
                    options.path = args[++i];
                } else if ("--format".equals(arg) && i + 1 < args.length) {
                    options.format = args[++i].toLowerCase();
                } else if ("--output".equals(arg) && i + 1 < args.length) {
                    options.output = args[++i];
                } else if ("--prefix".equals(arg) && i + 1 < args.length) {
                    options.prefix = args[++i];
                } else if ("--string-fields".equals(arg) && i + 1 < args.length) {
                    options.stringFields = args[++i];
                } else if ("--include-string-uuid".equals(arg)) {
                    options.includeStringUuidCandidates = true;
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    options.help = true;
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + arg);
                }
            }

            if (!"csv".equals(options.format) && !"json".equals(options.format)) {
                throw new IllegalArgumentException("Unsupported format: " + options.format);
            }
            return options;
        }
    }
}
