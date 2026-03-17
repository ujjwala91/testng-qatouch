package io.github.ujjwala91;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
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

    public QATouchClient(String domain, String apiToken, String projectKey) {
        this.domain = domain;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
    }

    // ── Test Cases ───────────────────────────────────────────────────────

    public List<JsonObject> getTestCases(String moduleKey) throws IOException {
        String endpoint = "/getAllTestCases/" + projectKey + "?moduleKey=" + enc(moduleKey) + "&page=1";
        JsonElement response = request("GET", endpoint);
        return normalizeList(response);
    }

    public void createTestCase(String moduleKey, String title, String description,
            String reference, String precondition, String stepsTemplate) throws IOException {
        String endpoint = "/testCase/steps"
                + "?projectKey=" + enc(projectKey)
                + "&sectionKey=" + enc(moduleKey)
                + "&caseTitle=" + enc(title)
                + "&description=" + enc(description)
                + "&reference=" + enc(reference)
                + "&precondition=" + enc(precondition)
                + "&steps_template=" + stepsTemplate;
        request("POST", endpoint);
    }

    // ── Milestones ───────────────────────────────────────────────────────

    public List<JsonObject> getMilestones() throws IOException {
        JsonElement response = request("GET", "/getAllMilestones/" + projectKey);
        return normalizeList(response);
    }

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

    public JsonObject createTestRun(String milestoneKey, String title, String assignTo,
            List<String> caseKeys, String tags) throws IOException {
        StringBuilder sb = new StringBuilder("/testRun/specific");
        sb.append("?projectKey=").append(enc(projectKey));
        sb.append("&milestoneKey=").append(enc(milestoneKey));
        sb.append("&testRun=").append(enc(title));
        sb.append("&assignTo=").append(enc(assignTo));
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

    // ── Helper class ─────────────────────────────────────────────────────

    public static class CaseResult {
        public final String caseKey;
        public final int statusId;

        public CaseResult(String caseKey, int statusId) {
            this.caseKey = caseKey;
            this.statusId = statusId;
        }
    }
}
