package br.com.vini.recomendadormusical.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class ApplicationProperties {

    private final OpenRouter openrouter = new OpenRouter();
    private final Memory memory = new Memory();
    private final Postgres postgres = new Postgres();
    private final LangSmith langsmith = new LangSmith();

    public OpenRouter getOpenrouter() {
        return openrouter;
    }

    public Memory getMemory() {
        return memory;
    }

    public Postgres getPostgres() {
        return postgres;
    }

    public LangSmith getLangsmith() {
        return langsmith;
    }

    public static class OpenRouter {
        private String apiKey = "";
        private String model = "arcee-ai/trinity-large-preview:free";
        private String baseUrl = "https://openrouter.ai/api/v1";
        private String httpReferer = "http://localhost:8080";
        private String xTitle = "poc_java_ia_recomendadormusical";
        private String providerSortBy = "throughput";
        private String providerPartition = "none";
        private double temperature = 0.7;
        private long timeoutSeconds = 90;
        private int maxRetries = 2;
        private boolean logRequests;
        private boolean logResponses;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getHttpReferer() { return httpReferer; }
        public void setHttpReferer(String httpReferer) { this.httpReferer = httpReferer; }
        public String getXTitle() { return xTitle; }
        public void setXTitle(String xTitle) { this.xTitle = xTitle; }
        public String getProviderSortBy() { return providerSortBy; }
        public void setProviderSortBy(String providerSortBy) { this.providerSortBy = providerSortBy; }
        public String getProviderPartition() { return providerPartition; }
        public void setProviderPartition(String providerPartition) { this.providerPartition = providerPartition; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
        public boolean isLogRequests() { return logRequests; }
        public void setLogRequests(boolean logRequests) { this.logRequests = logRequests; }
        public boolean isLogResponses() { return logResponses; }
        public void setLogResponses(boolean logResponses) { this.logResponses = logResponses; }
    }

    public static class Memory {
        private int maxMessagesBeforeSummary = 6;
        private String sqlitePreferencesPath = "./data/preferences.db";

        public int getMaxMessagesBeforeSummary() { return maxMessagesBeforeSummary; }
        public void setMaxMessagesBeforeSummary(int maxMessagesBeforeSummary) { this.maxMessagesBeforeSummary = maxMessagesBeforeSummary; }
        public String getSqlitePreferencesPath() { return sqlitePreferencesPath; }
        public void setSqlitePreferencesPath(String sqlitePreferencesPath) { this.sqlitePreferencesPath = sqlitePreferencesPath; }
    }

    public static class LangSmith {
        private boolean tracingEnabled;
        private String apiKey = "";
        private String project = "04-song-highlights-java";
        private String otlpEndpoint = "https://api.smith.langchain.com/otel/v1/traces";
        private String serviceName = "poc_java_ia_recomendadormusical";

        public boolean isTracingEnabled() { return tracingEnabled; }
        public void setTracingEnabled(boolean tracingEnabled) { this.tracingEnabled = tracingEnabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getProject() { return project; }
        public void setProject(String project) { this.project = project; }
        public String getOtlpEndpoint() { return otlpEndpoint; }
        public void setOtlpEndpoint(String otlpEndpoint) { this.otlpEndpoint = otlpEndpoint; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    }

    public static class Postgres {
        private String host = "localhost";
        private int port = 5432;
        private String database = "song_recommender";
        private String user = "postgres";
        private String password = "mysecretpassword";

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public String getDatabase() { return database; }
        public void setDatabase(String database) { this.database = database; }
        public String getUser() { return user; }
        public void setUser(String user) { this.user = user; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
