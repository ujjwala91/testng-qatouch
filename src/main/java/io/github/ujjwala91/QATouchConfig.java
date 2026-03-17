package io.github.ujjwala91;

/**
 * Configuration for the QA Touch TestNG listener.
 * Set values via system properties or pass directly.
 */
public class QATouchConfig {

    private final String domain;
    private final String apiToken;
    private final String projectKey;
    private final String testsuiteId;
    private final String assignTo;
    private final String milestoneName;
    private final String milestoneKey;
    private final boolean createCases;
    private final boolean createTestRun;
    private final String tag;

    private QATouchConfig(Builder builder) {
        this.domain = builder.domain;
        this.apiToken = builder.apiToken;
        this.projectKey = builder.projectKey;
        this.testsuiteId = builder.testsuiteId;
        this.assignTo = builder.assignTo;
        this.milestoneName = builder.milestoneName;
        this.milestoneKey = builder.milestoneKey;
        this.createCases = builder.createCases;
        this.createTestRun = builder.createTestRun;
        this.tag = builder.tag;
    }

    /**
     * Creates config from system properties / environment variables.
     * System properties take precedence over env vars.
     */
    public static QATouchConfig fromEnvironment() {
        return new Builder()
                .domain(resolve("QATOUCH_DOMAIN", "qatouch.domain"))
                .apiToken(resolve("QATOUCH_API_TOKEN", "qatouch.apiToken"))
                .projectKey(resolve("QATOUCH_PROJECT_KEY", "qatouch.projectKey"))
                .testsuiteId(resolve("QATOUCH_TESTSUITE_ID", "qatouch.testsuiteId"))
                .assignTo(resolve("QATOUCH_ASSIGN_TO", "qatouch.assignTo"))
                .milestoneName(resolveOrDefault("QATOUCH_MILESTONE_NAME", "qatouch.milestoneName", "TestNG Automation"))
                .milestoneKey(resolve("QATOUCH_MILESTONE_KEY", "qatouch.milestoneKey"))
                .createCases(!"false".equalsIgnoreCase(resolve("QATOUCH_CREATE_CASES", "qatouch.createCases")))
                .createTestRun(!"false".equalsIgnoreCase(resolve("QATOUCH_CREATE_TEST_RUN", "qatouch.createTestRun")))
                .tag(resolveOrDefault("QATOUCH_TAG", "qatouch.tag", "testng"))
                .build();
    }

    private static String resolve(String envKey, String sysPropKey) {
        String val = System.getProperty(sysPropKey);
        if (val != null && !val.isEmpty())
            return val;
        val = System.getenv(envKey);
        return (val != null && !val.isEmpty()) ? val : null;
    }

    private static String resolveOrDefault(String envKey, String sysPropKey, String defaultVal) {
        String val = resolve(envKey, sysPropKey);
        return (val != null) ? val : defaultVal;
    }

    public boolean isValid() {
        return domain != null && apiToken != null && projectKey != null
                && testsuiteId != null && assignTo != null;
    }

    public String getDomain() {
        return domain;
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getTestsuiteId() {
        return testsuiteId;
    }

    public String getAssignTo() {
        return assignTo;
    }

    public String getMilestoneName() {
        return milestoneName;
    }

    public String getMilestoneKey() {
        return milestoneKey;
    }

    public boolean isCreateCases() {
        return createCases;
    }

    public boolean isCreateTestRun() {
        return createTestRun;
    }

    public String getTag() {
        return tag;
    }

    public static class Builder {
        private String domain;
        private String apiToken;
        private String projectKey;
        private String testsuiteId;
        private String assignTo;
        private String milestoneName = "TestNG Automation";
        private String milestoneKey;
        private boolean createCases = true;
        private boolean createTestRun = true;
        private String tag = "testng";

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        public Builder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public Builder testsuiteId(String testsuiteId) {
            this.testsuiteId = testsuiteId;
            return this;
        }

        public Builder assignTo(String assignTo) {
            this.assignTo = assignTo;
            return this;
        }

        public Builder milestoneName(String milestoneName) {
            this.milestoneName = milestoneName;
            return this;
        }

        public Builder milestoneKey(String milestoneKey) {
            this.milestoneKey = milestoneKey;
            return this;
        }

        public Builder createCases(boolean createCases) {
            this.createCases = createCases;
            return this;
        }

        public Builder createTestRun(boolean createTestRun) {
            this.createTestRun = createTestRun;
            return this;
        }

        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        public QATouchConfig build() {
            return new QATouchConfig(this);
        }
    }
}
