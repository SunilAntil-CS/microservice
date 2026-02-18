package com.cqrs.policyquery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy-query")
public class PolicyQueryProperties {

    private Kafka kafka = new Kafka();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Pagination pagination = new Pagination();

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }
    public Elasticsearch getElasticsearch() { return elasticsearch; }
    public void setElasticsearch(Elasticsearch elasticsearch) { this.elasticsearch = elasticsearch; }
    public Pagination getPagination() { return pagination; }
    public void setPagination(Pagination pagination) { this.pagination = pagination; }

    public static class Kafka {
        private String topic = "policy-events";
        private String dlqTopic = "policy-events-dlq";

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getDlqTopic() { return dlqTopic; }
        public void setDlqTopic(String dlqTopic) { this.dlqTopic = dlqTopic; }
    }

    public static class Elasticsearch {
        private String indexPrefix = "policy-history";

        public String getIndexPrefix() { return indexPrefix; }
        public void setIndexPrefix(String indexPrefix) { this.indexPrefix = indexPrefix; }
    }

    public static class Pagination {
        private int defaultSize = 20;
        private int maxSize = 100;

        public int getDefaultSize() { return defaultSize; }
        public void setDefaultSize(int defaultSize) { this.defaultSize = defaultSize; }
        public int getMaxSize() { return maxSize; }
        public void setMaxSize(int maxSize) { this.maxSize = maxSize; }
    }
}
