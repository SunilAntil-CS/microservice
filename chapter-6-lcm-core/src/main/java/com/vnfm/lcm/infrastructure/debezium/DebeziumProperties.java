package com.vnfm.lcm.infrastructure.debezium;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the embedded Debezium CDC engine.
 * Bound to {@code lcm.debezium} in application.yml.
 */
@ConfigurationProperties(prefix = "lcm.debezium")
public class DebeziumProperties {

    private boolean enabled = true;

    private Database database = new Database();

    /**
     * Comma-separated list of tables to capture (e.g. public.events,public.outbox).
     */
    private String tableIncludeList = "public.events,public.outbox";

    /**
     * Kafka topic for change events from the {@code events} table.
     */
    private String eventsTopic = "vnf.events";

    /**
     * Connector name (used for offset storage and logging).
     */
    private String connectorName = "lcm-cdc-connector";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public String getTableIncludeList() {
        return tableIncludeList;
    }

    public void setTableIncludeList(String tableIncludeList) {
        this.tableIncludeList = tableIncludeList;
    }

    public String getEventsTopic() {
        return eventsTopic;
    }

    public void setEventsTopic(String eventsTopic) {
        this.eventsTopic = eventsTopic;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        this.connectorName = connectorName;
    }

    public static class Database {
        private String hostname = "localhost";
        private int port = 5432;
        private String username = "vnfm";
        private String password = "vnfm123";
        private String dbname = "vnfm_db";

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getDbname() {
            return dbname;
        }

        public void setDbname(String dbname) {
            this.dbname = dbname;
        }
    }
}
