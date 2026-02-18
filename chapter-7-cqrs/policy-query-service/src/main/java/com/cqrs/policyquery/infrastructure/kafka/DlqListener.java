package com.cqrs.policyquery.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to the dead letter topic and logs (and optionally alerts in production).
 */
@Component
public class DlqListener {

    private static final Logger log = LoggerFactory.getLogger(DlqListener.class);

    @KafkaListener(
            topics = "${policy-query.kafka.dlq-topic:policy-events-dlq}",
            groupId = "${spring.kafka.consumer.group-id:policy-query-service}-dlq"
    )
    public void onDlqMessage(String payload) {
        log.warn("DLQ message received (consider alerting): payloadLength={}", payload != null ? payload.length() : 0);
    }
}
