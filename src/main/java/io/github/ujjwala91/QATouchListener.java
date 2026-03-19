package io.github.ujjwala91;

import com.google.gson.JsonObject;
import org.testng.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TestNG listener that automatically syncs test cases, creates test runs,
 * and uploads results to QA Touch — no TR codes required.
 *
 * <p>
 * Usage in testng.xml:
 * 
 * <pre>{@code
 * <listeners>
 *   <listener class-name="io.github.ujjwala91.QATouchListener"/>
 * </listeners>
 * }</pre>
 *
 * <p>
 * Or programmatically:
 * 
 * <pre>{@code
 * TestNG tng = new TestNG();
 * tng.addListener(new QATouchListener());
 * }</pre>
 *
 * <p>
 * Configure via environment variables or system properties (see
 * {@link QATouchConfig}).
 */
public class QATouchListener implements ISuiteListener, ITestListener {

    /** Creates a new listener instance. */
    public QATouchListener() {
    }

    private QATouchConfig config;
    private QATouchClient client;
    private String testRunKey;

    /** normalizedTitle -> caseKey */
    private final Map<String, String> caseMap = new ConcurrentHashMap<>();

    /** normalizedTitle -> collected result */
    private final Map<String, CollectedResult> resultsByTitle = new ConcurrentHashMap<>();

    private boolean initialized = false;
    private boolean initFailed = false;

    // ── Suite lifecycle ──────────────────────────────────────────────────

    @Override
    public void onStart(ISuite suite) {
        config = QATouchConfig.fromEnvironment();
        if (!config.isValid()) {
            System.out.println("[qatouch] Missing required config. Listener disabled.");
            System.out.println(
                    "[qatouch] Set: QATOUCH_DOMAIN, QATOUCH_API_TOKEN, QATOUCH_PROJECT_KEY, QATOUCH_ASSIGN_TO, and QATOUCH_TESTSUITE_ID (or QATOUCH_SYNC=true to auto-create modules)");
            initFailed = true;
            return;
        }

        client = new QATouchClient(config.getDomain(), config.getApiToken(), config.getProjectKey());

        try {
            initialize(suite);
            initialized = true;
        } catch (IOException e) {
            System.err.println("[qatouch] Initialization failed: " + e.getMessage());
            initFailed = true;
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        if (initFailed || !initialized || testRunKey == null) {
            if (!initFailed) {
                System.err.println("[qatouch] No test run key available. Skipping upload.");
            }
            return;
        }

        List<QATouchClient.CaseResult> bulkResults = new ArrayList<>();
        List<Map.Entry<String, CollectedResult>> detailedResults = new ArrayList<>();

        for (Map.Entry<String, CollectedResult> entry : resultsByTitle.entrySet()) {
            String caseKey = caseMap.get(entry.getKey());
            if (caseKey == null) {
                continue;
            }
            CollectedResult cr = entry.getValue();

            // Use individual endpoint for failed tests with error details or screenshots
            if ("failed".equals(cr.status) && (cr.errorMessage != null || cr.screenshotPath != null)) {
                detailedResults.add(entry);
            } else {
                bulkResults.add(new QATouchClient.CaseResult(caseKey, statusToId(cr.status)));
            }
        }

        if (bulkResults.isEmpty() && detailedResults.isEmpty()) {
            return;
        }

        int uploadedCount = 0;
        // Upload failed tests individually with error messages and screenshots
        for (Map.Entry<String, CollectedResult> entry : detailedResults) {
            String caseKey = caseMap.get(entry.getKey());
            CollectedResult cr = entry.getValue();
            try {
                client.addResultWithComment(testRunKey, caseKey, statusToId(cr.status),
                        cr.errorMessage, cr.screenshotPath);
                uploadedCount++;
            } catch (IOException e) {
                System.err.println(
                        "[qatouch] Failed to upload detailed result for " + cr.title + ": " + e.getMessage());
                // Fall back to bulk for this one
                bulkResults.add(new QATouchClient.CaseResult(caseKey, statusToId(cr.status)));
            }
        }

        // Bulk upload the rest
        if (!bulkResults.isEmpty()) {
            try {
                JsonObject response = client.updateResults(testRunKey, bulkResults);
                if (response.has("error")) {
                    System.err.println("[qatouch] Bulk API error: " + response.get("error").getAsString()
                            + ". Falling back to individual uploads.");
                    throw new IOException(response.get("error").getAsString());
                }
                uploadedCount += bulkResults.size();
            } catch (IOException bulkError) {
                for (QATouchClient.CaseResult result : bulkResults) {
                    try {
                        client.addResultWithComment(testRunKey, result.caseKey, result.statusId,
                                null, null);
                        uploadedCount++;
                    } catch (IOException singleError) {
                        System.err.println("[qatouch] Failed individual upload for case " + result.caseKey
                                + ": " + singleError.getMessage());
                    }
                }
            }
        }

    }

    // ── Test lifecycle ───────────────────────────────────────────────────

    @Override
    public void onTestSuccess(ITestResult result) {
        collectResult(result, "passed");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        collectResult(result, "failed");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        collectResult(result, "untested");
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
        collectResult(result, "passed");
    }

    @Override
    public void onTestFailedWithTimeout(ITestResult result) {
        collectResult(result, "failed");
    }

    // ── Initialization ───────────────────────────────────────────────────

    private void initialize(ISuite suite) throws IOException {
        if (config.isSync()) {
            // Full sync: resolve modules, compare/create test cases
            Map<String, String> moduleMap = resolveModules(suite);

            Map<String, List<String>> titlesByModule = collectTestTitlesByModule(suite, moduleMap);
            if (titlesByModule.isEmpty()) {
                return;
            }

            for (Map.Entry<String, List<String>> entry : titlesByModule.entrySet()) {
                String moduleKey = entry.getKey();
                List<String> titles = entry.getValue();

                List<JsonObject> existingCases = client.getTestCases(moduleKey);
                for (JsonObject item : existingCases) {
                    String title = getStringField(item, "title", "case_title");
                    String key = getStringField(item, "case_key", "caseKey", "key", "api_key", "apiKey", "id");
                    if (title != null && key != null) {
                        caseMap.put(normalizeTitle(title), key);
                    }
                }

                if (config.isCreateCases()) {
                    for (String title : titles) {
                        String normalized = normalizeTitle(title);
                        if (caseMap.containsKey(normalized)) {
                            continue;
                        }

                        client.createTestCase(
                                moduleKey,
                                title,
                                "Automated TestNG scenario.",
                                "",
                                "TestNG environment is configured and the application under test is reachable.",
                                buildStepsTemplate(title));

                        List<JsonObject> refreshed = client.getTestCases(moduleKey);
                        for (JsonObject item : refreshed) {
                            String t = getStringField(item, "title", "case_title");
                            String k = getStringField(item, "case_key", "caseKey", "key", "api_key", "apiKey", "id");
                            if (t != null && k != null) {
                                caseMap.put(normalizeTitle(t), k);
                            }
                        }

                    }
                }
            }
        } else {
            // No sync: just fetch existing cases from configured module for result mapping
            if (config.getTestsuiteId() != null) {
                List<JsonObject> existingCases = client.getTestCases(config.getTestsuiteId());
                for (JsonObject item : existingCases) {
                    String title = getStringField(item, "title", "case_title");
                    String key = getStringField(item, "case_key", "caseKey", "key", "api_key", "apiKey", "id");
                    if (title != null && key != null) {
                        caseMap.put(normalizeTitle(title), key);
                    }
                }
            }
        }

        // Create test run (applies to both sync and no-sync modes)
        if (config.isCreateTestRun() && !caseMap.isEmpty()) {
            String milestoneKey = config.getMilestoneKey();
            if (milestoneKey == null) {
                milestoneKey = resolveMilestone(config.getMilestoneName());
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String runTitle = config.getMilestoneName() + " " + timestamp;

            // Always use explicit case keys so test cases are directly linked to the run
            List<String> caseKeys = new ArrayList<>(new LinkedHashSet<>(caseMap.values()));
            JsonObject run = client.createTestRun(milestoneKey, runTitle, config.getAssignTo(), caseKeys,
                    config.getTag());

            testRunKey = getStringField(run, "testrun_id", "key", "testRunKey", "testrunkey", "test_run");
            if (testRunKey == null) {
                System.err.println("[qatouch] Could not extract test run key from response: " + run);
            }
        }
    }

    private String resolveMilestone(String milestoneName) throws IOException {
        List<JsonObject> milestones = client.getMilestones();
        for (JsonObject m : milestones) {
            String name = getStringField(m, "milestone_name", "milestone", "name", "title");
            String key = getStringField(m, "milestone_key", "key", "milestoneKey", "api_key", "apiKey", "id");
            if (milestoneName.equalsIgnoreCase(name) && key != null) {
                return key;
            }
        }

        // Create milestone
        JsonObject created = client.createMilestone(milestoneName);
        String createdKey = getStringField(created, "milestone_key", "key", "milestoneKey", "api_key", "apiKey", "id");
        if (createdKey != null) {
            return createdKey;
        }

        milestones = client.getMilestones();
        for (JsonObject m : milestones) {
            String name = getStringField(m, "milestone_name", "milestone", "name", "title");
            String key = getStringField(m, "milestone_key", "key", "milestoneKey", "api_key", "apiKey", "id");
            if (milestoneName.equalsIgnoreCase(name) && key != null) {
                return key;
            }
        }
        throw new IOException("Failed to resolve milestone: " + milestoneName);
    }

    /**
     * Resolves QA Touch modules. When sync is enabled and no testsuiteId is set,
     * each {@code <test>} block in the suite becomes a QA Touch module (created
     * if missing). Otherwise, falls back to the configured testsuiteId.
     */
    private Map<String, String> resolveModules(ISuite suite) throws IOException {
        // testName -> moduleKey
        Map<String, String> moduleMap = new LinkedHashMap<>();

        if (config.getTestsuiteId() != null) {
            // Use the single configured testsuite/module for everything
            for (org.testng.xml.XmlTest xmlTest : suite.getXmlSuite().getTests()) {
                moduleMap.put(xmlTest.getName(), config.getTestsuiteId());
            }
            if (moduleMap.isEmpty() && config.getTestsuiteId() != null) {
                moduleMap.put(suite.getName(), config.getTestsuiteId());
            }
            return moduleMap;
        }

        // Fetch existing modules from QA Touch
        List<JsonObject> existingModules = client.getModules();
        Map<String, String> existingByName = new LinkedHashMap<>();
        for (JsonObject m : existingModules) {
            String name = getStringField(m, "section_name", "module_name", "name", "title", "sectionName");
            String key = getStringField(m, "section_key", "module_key", "key", "sectionKey", "api_key", "apiKey", "id");
            if (name != null && key != null) {
                existingByName.put(name.toLowerCase().trim(), key);
            }
        }

        // Use the configured testsuiteId as parent for new child modules, if set
        String parentKey = config.getTestsuiteId();

        for (org.testng.xml.XmlTest xmlTest : suite.getXmlSuite().getTests()) {
            String testName = xmlTest.getName();
            String normalizedName = testName.toLowerCase().trim();

            if (existingByName.containsKey(normalizedName)) {
                String key = existingByName.get(normalizedName);
                moduleMap.put(testName, key);
            } else {
                JsonObject created = client.createModule(testName, parentKey);
                String createdKey = getStringField(created, "section_key", "module_key", "key", "sectionKey", "api_key",
                        "apiKey",
                        "id");

                if (createdKey == null) {
                    // Re-fetch modules to find the newly created one
                    existingModules = client.getModules();
                    for (JsonObject m : existingModules) {
                        String n = getStringField(m, "section_name", "module_name", "name", "title", "sectionName");
                        String k = getStringField(m, "section_key", "module_key", "key", "sectionKey", "api_key",
                                "apiKey", "id");
                        if (n != null && k != null) {
                            existingByName.put(n.toLowerCase().trim(), k);
                        }
                    }
                    createdKey = existingByName.get(normalizedName);
                }

                if (createdKey != null) {
                    moduleMap.put(testName, createdKey);
                    existingByName.put(normalizedName, createdKey);
                } else {
                    System.err.println("[qatouch] Failed to create module: " + testName);
                }
            }
        }

        return moduleMap;
    }

    /**
     * Groups test titles by their target module key based on which {@code <test>}
     * block they belong to.
     */
    private Map<String, List<String>> collectTestTitlesByModule(
            ISuite suite, Map<String, String> moduleMap) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        for (org.testng.xml.XmlTest xmlTest : suite.getXmlSuite().getTests()) {
            String moduleKey = moduleMap.get(xmlTest.getName());
            if (moduleKey == null)
                continue;

            List<String> titles = result.computeIfAbsent(moduleKey, k -> new ArrayList<>());
            // Collect methods from this <test> block
            for (org.testng.xml.XmlClass xmlClass : xmlTest.getClasses()) {
                try {
                    Class<?> clazz = Class.forName(xmlClass.getName());
                    for (java.lang.reflect.Method method : clazz.getMethods()) {
                        org.testng.annotations.Test testAnnotation = method
                                .getAnnotation(org.testng.annotations.Test.class);
                        if (testAnnotation != null) {
                            String title = (testAnnotation.description() != null
                                    && !testAnnotation.description().isEmpty())
                                            ? testAnnotation.description()
                                            : method.getName();
                            titles.add(title);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    System.err.println("[qatouch] Class not found: " + xmlClass.getName());
                }
            }
        }

        // Fallback: if no <test> blocks resolved, use all methods with the first
        // available module
        if (result.isEmpty() && !moduleMap.isEmpty()) {
            String fallbackKey = moduleMap.values().iterator().next();
            result.put(fallbackKey, collectTestTitles(suite));
        }

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void collectResult(ITestResult result, String status) {
        String title = result.getMethod().getMethodName();
        // Use @description if available, otherwise method name
        if (result.getMethod().getDescription() != null && !result.getMethod().getDescription().isEmpty()) {
            title = result.getMethod().getDescription();
        }
        String normalized = normalizeTitle(title);

        // Extract error message from throwable
        String errorMessage = null;
        if (result.getThrowable() != null) {
            Throwable t = result.getThrowable();
            StringBuilder sb = new StringBuilder();
            sb.append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            StackTraceElement[] trace = t.getStackTrace();
            for (int i = 0; i < Math.min(trace.length, 5); i++) {
                sb.append("\n  at ").append(trace[i]);
            }
            errorMessage = sb.toString();
        }

        // Extract screenshot path from test attribute
        String screenshotPath = null;
        Object screenshotAttr = result.getAttribute("screenshot");
        if (screenshotAttr != null) {
            screenshotPath = screenshotAttr.toString();
        }

        final String errMsg = errorMessage;
        final String ssPath = screenshotPath;
        resultsByTitle.merge(normalized, new CollectedResult(title, status, errorMessage, screenshotPath),
                (existing, incoming) -> {
                    existing.status = combineStatuses(existing.status, incoming.status);
                    if (errMsg != null)
                        existing.errorMessage = errMsg;
                    if (ssPath != null)
                        existing.screenshotPath = ssPath;
                    return existing;
                });
    }

    private List<String> collectTestTitles(ISuite suite) {
        Set<String> titles = new LinkedHashSet<>();
        for (Map.Entry<String, Collection<ITestNGMethod>> entry : suite.getMethodsByGroups().entrySet()) {
            for (ITestNGMethod method : entry.getValue()) {
                String title = (method.getDescription() != null && !method.getDescription().isEmpty())
                        ? method.getDescription()
                        : method.getMethodName();
                titles.add(title);
            }
        }
        // Also collect from all tests directly
        for (ITestNGMethod method : suite.getAllMethods()) {
            String title = (method.getDescription() != null && !method.getDescription().isEmpty())
                    ? method.getDescription()
                    : method.getMethodName();
            titles.add(title);
        }
        return new ArrayList<>(titles);
    }

    static String normalizeTitle(String title) {
        return title.replaceAll("^TR\\d+\\s*", "")
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private static String combineStatuses(String current, String next) {
        Map<String, Integer> priority = Map.of(
                "failed", 4, "blocked", 3, "passed", 2, "untested", 1);
        int currentPriority = priority.getOrDefault(current, 0);
        int nextPriority = priority.getOrDefault(next, 0);
        return nextPriority > currentPriority ? next : current;
    }

    private static int statusToId(String status) {
        switch (status) {
            case "passed":
                return 1;
            case "failed":
                return 5;
            case "blocked":
                return 3;
            default:
                return 2; // untested
        }
    }

    private static String getStringField(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.get(key).isJsonNull()) {
                String val = obj.get(key).getAsString();
                if (val != null && !val.isEmpty())
                    return val.trim();
            }
        }
        return null;
    }

    private static String buildStepsTemplate(String title) {
        String steps = "{\"0\":{\"steps\":\"Execute the scenario: " + escapeJson(title)
                + "\",\"expected_result\":\"The automated actions complete without unexpected errors.\"},"
                + "\"1\":{\"steps\":\"Validate the expected result for " + escapeJson(title)
                + "\",\"expected_result\":\"The final assertion passes successfully.\"}}";
        return URLEncoder.encode(steps, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ── Inner class ──────────────────────────────────────────────────────

    private static class CollectedResult {
        final String title;
        String status;
        String errorMessage;
        String screenshotPath;

        CollectedResult(String title, String status, String errorMessage, String screenshotPath) {
            this.title = title;
            this.status = status;
            this.errorMessage = errorMessage;
            this.screenshotPath = screenshotPath;
        }
    }
}
