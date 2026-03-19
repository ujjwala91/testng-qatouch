package io.github.ujjwala91;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;

/**
 * HTTP client for the QA Touch REST API.
 * Uses the same endpoints as the playwright-qatouch npm package.
 */
public class QATouchClient {

    private static final String BASE_URL = "https://api.qatouch.com/api/v1";

    private final String domain;
    private final String apiToken;
    private final String projectKey;
    private final Gson gson = new Gson();

    /**
     * Creates a new QA Touch API client.
     *
     * @param domain     the QA Touch domain
     * @param apiToken   the API authentication token
     * @param projectKey the project key
     */
    public QATouchClient(String domain, String apiToken, String projectKey) {
        this.domain = domain;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
    }

    // ── Test Cases ───────────────────────────────────────────────────────

    /**
     * Retrieves all test cases for a given module.
     *
     * @param moduleKey the module key to fetch cases from
     * @return list of test case JSON objects
     * @throws IOException if the API call fails
     */
    public List<JsonObject> getTestCases(String moduleKey) throws IOException {
        List<JsonObject> all = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            String endpoint = "/getAllTestCases/" + projectKey + "?moduleKey=" + enc(moduleKey) + "&page=" + page;
            JsonElement response = request("GET", endpoint);
            List<JsonObject> batch = normalizeList(response);
            if (batch.isEmpty())
                break;
            all.addAll(batch);
            int total = getMetaInt(response, "total");
            if (total > 0 && all.size() >= total)
                break;
            int perPage = getMetaInt(response, "per_page");
            if (perPage > 0 && batch.size() < perPage)
                break;
            page++;
            if (page > 200)
                break;
        }
        return all;
    }

    /**
     * Creates a new test case with step template in the given module.
     *
     * @param moduleKey     the module key to create the case under
     * @param title         the test case title
     * @param description   the test case description
     * @param reference     the test case reference
     * @param precondition  the test case precondition
     * @param stepsTemplate URL-encoded JSON steps template
     * @throws IOException if the API call fails
     */
    public void createTestCase(String moduleKey, String title, String description,
            String reference, String precondition, String stepsTemplate) throws IOException {
        String endpoint = "/testCase/steps"
                + "?projectKey=" + enc(projectKey)
                + "&sectionKey=" + enc(moduleKey)
                + "&caseTitle=" + enc(title)
                + "&description=" + enc(description)
                + "&reference=" + enc(reference)
                + "&precondition=" + enc(precondition)
                + "&steps_template=" + stepsTemplate
                + "&mode=automation";
        request("POST", endpoint);
    }

    // ── Modules / Sections ───────────────────────────────────────────────

    /**
     * Retrieves all modules for the project.
     *
     * @return list of module JSON objects
     * @throws IOException if the API call fails
     */
    public List<JsonObject> getModules() throws IOException {
        List<JsonObject> all = new java.util.ArrayList<>();
        int page = 1;
        while (true) {
            JsonElement response = request("GET", "/getAllModules/" + projectKey + "?page=" + page);
            List<JsonObject> batch = normalizeList(response);
            if (batch.isEmpty())
                break;
            all.addAll(batch);
            int total = getMetaInt(response, "total");
            if (total > 0 && all.size() >= total)
                break;
            int perPage = getMetaInt(response, "per_page");
            if (perPage > 0 && batch.size() < perPage)
                break;
            page++;
            if (page > 50)
                break;
        }
        return all;
    }

    /**
     * Creates a new module in the project.
     *
     * @param moduleName the name of the module to create
     * @param parentKey  optional parent module key for creating child modules
     * @return the created module as a JSON object
     * @throws IOException if the API call fails
     */
    public JsonObject createModule(String moduleName, String parentKey) throws IOException {
        StringBuilder sb = new StringBuilder("/module");
        sb.append("?projectKey=").append(enc(projectKey));
        sb.append("&moduleName=").append(enc(moduleName));
        if (parentKey != null && !parentKey.isEmpty()) {
            sb.append("&parentKey=").append(enc(parentKey));
        }
        JsonElement response = request("POST", sb.toString());
        if (response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("data") && obj.get("data").isJsonObject()) {
                return obj.getAsJsonObject("data");
            }
            return obj;
        }
        List<JsonObject> list = normalizeList(response);
        return list.isEmpty() ? new JsonObject() : list.get(0);
    }

    // ── Milestones ───────────────────────────────────────────────────────

    /**
     * Retrieves all milestones (releases) for the project.
     *
     * @return list of milestone JSON objects
     * @throws IOException if the API call fails
     */
    public List<JsonObject> getMilestones() throws IOException {
        JsonElement response = request("GET", "/getAllMilestones/" + projectKey);
        return normalizeList(response);
    }

    /**
     * Creates a new milestone (release) in the project.
     *
     * @param name the milestone name
     * @return the created milestone as a JSON object
     * @throws IOException if the API call fails
     */
    public JsonObject createMilestone(String name) throws IOException {
        JsonElement response = request("POST", "/milestone?projectKey=" + enc(projectKey) + "&milestone=" + enc(name));
        if (response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("data") && obj.get("data").isJsonObject()) {
                return obj.getAsJsonObject("data");
            }
            return obj;
        }
        List<JsonObject> list = normalizeList(response);
        return list.isEmpty() ? new JsonObject() : list.get(0);
    }

    // ── Test Runs ────────────────────────────────────────────────────────

    /**
     * Creates a test run with specific test cases.
     *
     * @param milestoneKey the milestone key to associate
     * @param title        the test run title
     * @param assignTo     the user key to assign the run to
     * @param caseKeys     list of test case keys to include
     * @param tags         optional tags for the test run
     * @return the created test run as a JSON object
     * @throws IOException if the API call fails
     */
    public JsonObject createTestRun(String milestoneKey, String title, String assignTo,
            List<String> caseKeys, String tags) throws IOException {
        StringBuilder sb = new StringBuilder("/testRun/specific");
        sb.append("?projectKey=").append(enc(projectKey));
        sb.append("&milestoneKey=").append(enc(milestoneKey));
        sb.append("&testRun=").append(enc(title));
        sb.append("&assignTo=").append(enc(assignTo));
        sb.append("&mode=automation");
        if (tags != null && !tags.isEmpty()) {
            sb.append("&tags=").append(enc(tags));
        }
        for (String key : caseKeys) {
            sb.append("&caseId[]=").append(enc(key));
        }
        JsonElement response = request("POST", sb.toString());
        List<JsonObject> list = normalizeList(response);
        if (!list.isEmpty())
            return list.get(0);
        if (response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("data")) {
                JsonElement data = obj.get("data");
                if (data.isJsonArray() && data.getAsJsonArray().size() > 0) {
                    return data.getAsJsonArray().get(0).getAsJsonObject();
                }
                if (data.isJsonObject())
                    return data.getAsJsonObject();
            }
        }
        return new JsonObject();
    }

    // ── Results ──────────────────────────────────────────────────────────

    /**
     * Creates a test run from specific modules with mode=automation,
     * so all automation test cases in those modules are included.
     *
     * @param milestoneKey the milestone key to associate
     * @param title        the test run title
     * @param assignTo     the user key to assign the run to
     * @param moduleKeys   list of module keys to include
     * @param tags         optional tags for the test run
     * @return the created test run as a JSON object
     * @throws IOException if the API call fails
     */
    public JsonObject createTestRunByModules(String milestoneKey, String title, String assignTo,
            List<String> moduleKeys, String tags) throws IOException {
        StringBuilder sb = new StringBuilder("/testRun/specific/module/mode");
        sb.append("?projectKey=").append(enc(projectKey));
        sb.append("&milestoneKey=").append(enc(milestoneKey));
        sb.append("&testRun=").append(enc(title));
        sb.append("&assignTo=").append(enc(assignTo));
        sb.append("&mode=automation");
        if (tags != null && !tags.isEmpty()) {
            sb.append("&tags=").append(enc(tags));
        }
        for (String key : moduleKeys) {
            sb.append("&moduleKey[]=").append(enc(key));
        }
        JsonElement response = request("POST", sb.toString());
        List<JsonObject> list = normalizeList(response);
        if (!list.isEmpty())
            return list.get(0);
        if (response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("data")) {
                JsonElement data = obj.get("data");
                if (data.isJsonArray() && data.getAsJsonArray().size() > 0) {
                    return data.getAsJsonArray().get(0).getAsJsonObject();
                }
                if (data.isJsonObject())
                    return data.getAsJsonObject();
            }
        }
        return new JsonObject();
    }

    // ── Results ──────────────────────────────────────────────────────────
    /**
     * Updates the status of multiple test run results in bulk.
     *
     * @param testRunKey the test run key
     * @param results    list of case results with status
     * @return the API response as a JSON object
     * @throws IOException if the API call fails
     */

    public JsonObject updateResults(String testRunKey, List<CaseResult> results) throws IOException {
        JsonObject casesObj = new JsonObject();
        for (int i = 0; i < results.size(); i++) {
            CaseResult r = results.get(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("case", r.caseKey);
            entry.addProperty("status", r.statusId);
            casesObj.add(String.valueOf(i), entry);
        }
        String endpoint = "/runresult/updatestatus/multiple"
                + "?project=" + enc(projectKey)
                + "&test_run=" + enc(testRunKey)
                + "&cases=" + enc(gson.toJson(casesObj));
        JsonElement response = request("POST", endpoint);
        return response.isJsonObject() ? response.getAsJsonObject() : new JsonObject();
    }

    /**
     * Adds a result for a single test case with optional comments and file
     * attachment.
     * Uses the /testRunResults/add/results endpoint which supports multipart file
     * uploads.
     *
     * @param testRunKey     the test run key
     * @param caseKey        the test case key
     * @param statusId       the status ID (1=passed, 5=failed, etc.)
     * @param comments       optional comment text (e.g. error message)
     * @param screenshotPath optional path to a screenshot file to attach
     * @return the API response as a JSON object
     * @throws IOException if the API call fails
     */
    public JsonObject addResultWithComment(String testRunKey, String caseKey, int statusId,
            String comments, String screenshotPath) throws IOException {
        String statusName = statusIdToName(statusId);
        StringBuilder sb = new StringBuilder("/testRunResults/add/results");
        sb.append("?status=").append(enc(statusName));
        sb.append("&project=").append(enc(projectKey));
        sb.append("&test_run=").append(enc(testRunKey));
        sb.append("&run_result[]=CASE").append(enc(caseKey));
        if (comments != null && !comments.isEmpty()) {
            sb.append("&comments=").append(enc(comments));
        }

        if (screenshotPath != null && !screenshotPath.isEmpty()) {
            Path file = Paths.get(screenshotPath);
            if (Files.exists(file) && Files.size(file) <= 2 * 1024 * 1024) {
                return multipartRequest(sb.toString(), file);
            }
        }

        JsonElement response = request("POST", sb.toString());
        return response.isJsonObject() ? response.getAsJsonObject() : new JsonObject();
    }

    private static String statusIdToName(int id) {
        switch (id) {
            case 1:
                return "passed";
            case 3:
                return "blocked";
            case 4:
                return "retest";
            case 5:
                return "failed";
            case 6:
                return "not-applicable";
            case 7:
                return "in-progress";
            default:
                return "untested";
        }
    }

    // ── HTTP plumbing ────────────────────────────────────────────────────

    private JsonElement request(String method, String endpoint) throws IOException {
        URL url = new URL(BASE_URL + endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("api-token", apiToken);
        conn.setRequestProperty("domain", domain);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoInput(true);

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();

        if (body == null || body.isEmpty())
            return new JsonObject();

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(body);
        } catch (JsonSyntaxException e) {
            throw new IOException("Non-JSON response (" + status + ") for " + endpoint);
        }

        if (status >= 200 && status < 300)
            return parsed;

        String errorMsg = "API Error " + status + " for " + endpoint;
        if (parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            if (obj.has("error_msg"))
                errorMsg = obj.get("error_msg").getAsString();
            else if (obj.has("error"))
                errorMsg = obj.get("error").getAsString();
        }
        throw new IOException(errorMsg);
    }

    private String readStream(InputStream is) throws IOException {
        if (is == null)
            return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line);
            return sb.toString();
        }
    }

    private JsonObject multipartRequest(String endpoint, Path file) throws IOException {
        String boundary = "----QATouchBoundary" + System.currentTimeMillis();
        URL url = new URL(BASE_URL + endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("api-token", apiToken);
        conn.setRequestProperty("domain", domain);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);
        conn.setDoInput(true);

        String fileName = file.getFileName().toString();
        String mimeType = Files.probeContentType(file);
        if (mimeType == null)
            mimeType = "application/octet-stream";

        try (OutputStream os = conn.getOutputStream()) {
            // File part
            os.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            os.write(("Content-Disposition: form-data; name=\"file[]\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            os.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            Files.copy(file, os);
            os.write("\r\n".getBytes(StandardCharsets.UTF_8));
            // Closing boundary
            os.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
        String body = readStream(is);
        conn.disconnect();

        if (body == null || body.isEmpty())
            return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (status >= 200 && status < 300) {
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            }
            throw new IOException("API Error " + status + " for " + endpoint);
        } catch (JsonSyntaxException e) {
            throw new IOException("Non-JSON response (" + status + ") for " + endpoint);
        }
    }

    private List<JsonObject> normalizeList(JsonElement response) {
        if (response.isJsonArray()) {
            return gson.fromJson(response, new TypeToken<List<JsonObject>>() {
            }.getType());
        }
        if (response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("data") && obj.get("data").isJsonArray()) {
                return gson.fromJson(obj.get("data"), new TypeToken<List<JsonObject>>() {
                }.getType());
            }
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                return gson.fromJson(obj.get("items"), new TypeToken<List<JsonObject>>() {
                }.getType());
            }
        }
        return Collections.emptyList();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private int getMetaInt(JsonElement response, String field) {
        if (response != null && response.isJsonObject()) {
            JsonObject obj = response.getAsJsonObject();
            if (obj.has("meta") && obj.get("meta").isJsonObject()) {
                JsonObject meta = obj.getAsJsonObject("meta");
                if (meta.has(field)) {
                    try {
                        return meta.get(field).getAsInt();
                    } catch (Exception e) {
                        String s = meta.get(field).getAsString();
                        try {
                            return Integer.parseInt(s);
                        } catch (NumberFormatException ignored) {
                            // ignore
                        }
                    }
                }
            }
        }
        return 0;
    }

    // ── Helper class ─────────────────────────────────────────────────────

    /**
     * Holds a test case key and its result status ID.
     */
    public static class CaseResult {
        /** The test case key. */
        public final String caseKey;
        /** The status ID (1=passed, 2=untested, 3=blocked, 5=failed). */
        public final int statusId;

        /**
         * Creates a new case result.
         *
         * @param caseKey  the test case key
         * @param statusId the status ID
         */
        public CaseResult(String caseKey, int statusId) {
            this.caseKey = caseKey;
            this.statusId = statusId;
        }
    }
}
