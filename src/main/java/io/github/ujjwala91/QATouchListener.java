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
                    "[qatouch] Set: QATOUCH_DOMAIN, QATOUCH_API_TOKEN, QATOUCH_PROJECT_KEY, QATOUCH_TESTSUITE_ID, QATOUCH_ASSIGN_TO");
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

        List<QATouchClient.CaseResult> results = new ArrayList<>();
        for (Map.Entry<String, CollectedResult> entry : resultsByTitle.entrySet()) {
            String caseKey = caseMap.get(entry.getKey());
            if (caseKey == null) {
                System.out.println("[qatouch] Skipping unmapped test: " + entry.getValue().title);
                continue;
            }
            CollectedResult cr = entry.getValue();
            results.add(new QATouchClient.CaseResult(caseKey, statusToId(cr.status)));
            System.out.println("[qatouch] " + cr.title + " -> " + cr.status);
        }

        if (results.isEmpty()) {
            System.out.println("[qatouch] No results to upload.");
            return;
        }

        try {
            JsonObject response = client.updateResults(testRunKey, results);
            if (response.has("error")) {
                System.err.println("[qatouch] API error: " + response.get("error").getAsString());
            } else {
                System.out.println("[qatouch] Uploaded " + results.size() + " result(s) to test run " + testRunKey);
            }
        } catch (IOException e) {
            System.err.println("[qatouch] Upload failed: " + e.getMessage());
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
        // 1. Collect all test method names from the suite
        List<String> testTitles = collectTestTitles(suite);
        if (testTitles.isEmpty()) {
            System.out.println("[qatouch] No tests found in suite.");
            return;
        }

        // 2. Fetch existing test cases from QA Touch
        List<JsonObject> existingCases = client.getTestCases(config.getTestsuiteId());
        for (JsonObject item : existingCases) {
            String title = getStringField(item, "title", "case_title");
            String key = getStringField(item, "case_key", "caseKey", "key", "api_key", "apiKey", "id");
            if (title != null && key != null) {
                caseMap.put(normalizeTitle(title), key);
            }
        }

        // 3. Create missing test cases
        if (config.isCreateCases()) {
            for (String title : testTitles) {
                String normalized = normalizeTitle(title);
                if (caseMap.containsKey(normalized)) {
                    System.out.println("[qatouch] Matched: " + title + " -> " + caseMap.get(normalized));
                    continue;
                }

                System.out.println("[qatouch] Creating case: " + title);
                client.createTestCase(
                        config.getTestsuiteId(),
                        title,
                        "Automated TestNG scenario.",
                        "",
                        "TestNG environment is configured and the application under test is reachable.",
                        buildStepsTemplate(title));

                // Re-fetch to get the new key
                List<JsonObject> refreshed = client.getTestCases(config.getTestsuiteId());
                for (JsonObject item : refreshed) {
                    String t = getStringField(item, "title", "case_title");
                    String k = getStringField(item, "case_key", "caseKey", "key", "api_key", "apiKey", "id");
                    if (t != null && k != null) {
                        caseMap.put(normalizeTitle(t), k);
                    }
                }

                if (caseMap.containsKey(normalized)) {
                    System.out.println("[qatouch] Created: " + title + " -> " + caseMap.get(normalized));
                }
            }
        }

        // 4. Create test run
        if (config.isCreateTestRun() && !caseMap.isEmpty()) {
            // Resolve milestone
            String milestoneKey = config.getMilestoneKey();
            if (milestoneKey == null) {
                milestoneKey = resolveMilestone(config.getMilestoneName());
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String runTitle = config.getMilestoneName() + " " + timestamp;

            List<String> caseKeys = new ArrayList<>(caseMap.values());
            JsonObject run = client.createTestRun(milestoneKey, runTitle, config.getAssignTo(), caseKeys,
                    config.getTag());

            testRunKey = getStringField(run, "testrun_id", "key", "testRunKey", "testrunkey", "test_run");
            if (testRunKey != null) {
                System.out.println("[qatouch] Created test run: " + testRunKey);
            } else {
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

    // ── Helpers ──────────────────────────────────────────────────────────

    private void collectResult(ITestResult result, String status) {
        String title = result.getMethod().getMethodName();
        // Use @description if available, otherwise method name
        if (result.getMethod().getDescription() != null && !result.getMethod().getDescription().isEmpty()) {
            title = result.getMethod().getDescription();
        }
        String normalized = normalizeTitle(title);

        resultsByTitle.merge(normalized, new CollectedResult(title, status),
                (existing, incoming) -> {
                    existing.status = combineStatuses(existing.status, incoming.status);
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

        CollectedResult(String title, String status) {
            this.title = title;
            this.status = status;
        }
    }
}
