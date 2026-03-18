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
    private final boolean sync;
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
        this.sync = builder.sync;
        this.createCases = builder.createCases;
        this.createTestRun = builder.createTestRun;
        this.tag = builder.tag;
    }

    /**
     * Creates config from system properties / environment variables.
     * System properties take precedence over env vars.
     *
     * @return a new config populated from the environment
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
                .sync(!"false".equalsIgnoreCase(resolve("QATOUCH_SYNC", "qatouch.sync")))
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

    /**
     * Checks whether all required configuration fields are set.
     * 
     * @return true if the config is valid
     */
    public boolean isValid() {
        return domain != null && apiToken != null && projectKey != null
                && assignTo != null
                && (testsuiteId != null || sync);
    }

    /**
     * Returns the QA Touch domain.
     * 
     * @return the domain
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the API authentication token.
     * 
     * @return the token
     */
    public String getApiToken() {
        return apiToken;
    }

    /**
     * Returns the project key.
     * 
     * @return the project key
     */
    public String getProjectKey() {
        return projectKey;
    }

    /**
     * Returns the testsuite/module ID.
     * 
     * @return the ID
     */
    public String getTestsuiteId() {
        return testsuiteId;
    }

    /**
     * Returns the user key to assign test runs to.
     * 
     * @return the user key
     */
    public String getAssignTo() {
        return assignTo;
    }

    /**
     * Returns the milestone name.
     * 
     * @return the name
     */
    public String getMilestoneName() {
        return milestoneName;
    }

    /**
     * Returns the milestone key.
     * 
     * @return the key
     */
    public String getMilestoneKey() {
        return milestoneKey;
    }

    /**
     * Returns whether full sync (module/case creation) is enabled.
     * 
     * @return true if enabled
     */
    public boolean isSync() {
        return sync;
    }

    /**
     * Returns whether missing test cases should be auto-created.
     * 
     * @return true if enabled
     */
    public boolean isCreateCases() {
        return createCases;
    }

    /**
     * Returns whether a test run should be created automatically.
     * 
     * @return true if enabled
     */
    public boolean isCreateTestRun() {
        return createTestRun;
    }

    /**
     * Returns the tag to apply to test runs.
     * 
     * @return the tag
     */
    public String getTag() {
        return tag;
    }

    /** Builder for constructing {@link QATouchConfig} instances. */
    public static class Builder {
        private String domain;
        private String apiToken;
        private String projectKey;
        private String testsuiteId;
        private String assignTo;
        private String milestoneName = "TestNG Automation";
        private String milestoneKey;
        private boolean sync = true;
        private boolean createCases = true;
        private boolean createTestRun = true;
        private String tag = "testng";

        /** Creates a new builder with default values. */
        public Builder() {
        }

        /**
         * Sets the QA Touch domain.
         * 
         * @param domain the domain
         * @return this builder
         */
        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets the API token.
         * 
         * @param apiToken the token
         * @return this builder
         */
        public Builder apiToken(String apiToken) {
            this.apiToken = apiToken;
            return this;
        }

        /**
         * Sets the project key.
         * 
         * @param projectKey the key
         * @return this builder
         */
        public Builder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        /**
         * Sets the testsuite/module ID.
         * 
         * @param testsuiteId the ID
         * @return this builder
         */
        public Builder testsuiteId(String testsuiteId) {
            this.testsuiteId = testsuiteId;
            return this;
        }

        /**
         * Sets the user key to assign runs to.
         * 
         * @param assignTo the user key
         * @return this builder
         */
        public Builder assignTo(String assignTo) {
            this.assignTo = assignTo;
            return this;
        }

        /**
         * Sets the milestone name.
         * 
         * @param milestoneName the name
         * @return this builder
         */
        public Builder milestoneName(String milestoneName) {
            this.milestoneName = milestoneName;
            return this;
        }

        /**
         * Sets the milestone key.
         * 
         * @param milestoneKey the key
         * @return this builder
         */
        public Builder milestoneKey(String milestoneKey) {
            this.milestoneKey = milestoneKey;
            return this;
        }

        /**
         * Enables or disables full sync.
         * 
         * @param sync true to enable
         * @return this builder
         */
        public Builder sync(boolean sync) {
            this.sync = sync;
            return this;
        }

        /**
         * Enables or disables auto-creation of cases.
         * 
         * @param createCases true to enable
         * @return this builder
         */
        public Builder createCases(boolean createCases) {
            this.createCases = createCases;
            return this;
        }

        /**
         * Enables or disables auto-creation of test runs.
         * 
         * @param createTestRun true to enable
         * @return this builder
         */
        public Builder createTestRun(boolean createTestRun) {
            this.createTestRun = createTestRun;
            return this;
        }

        /**
         * Sets the tag for test runs.
         * 
         * @param tag the tag
         * @return this builder
         */
        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Builds the config.
         * 
         * @return the constructed config
         */
        public QATouchConfig build() {
            return new QATouchConfig(this);
        }
    }
}
