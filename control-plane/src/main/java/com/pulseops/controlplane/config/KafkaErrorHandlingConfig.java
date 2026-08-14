package com.pulseops.controlplane.config;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

@Configuration
class KafkaErrorHandlingConfig {

    @Bean
    DeadLetterTransport deadLetterTransport(KafkaProperties kafkaProperties) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildProducerProperties());
        var producerFactory = new DefaultKafkaProducerFactory<>(
                properties,
                new StringSerializer(),
                new StringSerializer()
        );
        return new DeadLetterTransport(producerFactory);
    }

    @Bean
    CommonErrorHandler kafkaErrorHandler(
            DeadLetterTransport transport,
            MeterRegistry meterRegistry,
            @Value("${pulseops.kafka.results-topic}") String resultsTopic,
            @Value("${pulseops.kafka.results-dlq-topic}") String resultsDlqTopic,
            @Value("${pulseops.kafka.heartbeats-topic}") String heartbeatsTopic,
            @Value("${pulseops.kafka.heartbeats-dlq-topic}") String heartbeatsDlqTopic,
            @Value("${pulseops.kafka.retry.max-attempts}") int maxAttempts,
            @Value("${pulseops.kafka.retry.initial-backoff}") Duration initialBackoff,
            @Value("${pulseops.kafka.retry.max-backoff}") Duration maxBackoff
    ) {
        Map<String, String> deadLetterTopics = Map.of(
                resultsTopic, resultsDlqTopic,
                heartbeatsTopic, heartbeatsDlqTopic
        );
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                transport.template(),
                (record, exception) -> deadLetterDestination(record, deadLetterTopics, meterRegistry)
        );
        recoverer.setFailIfSendResultIsError(true);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(
                Math.max(0, maxAttempts - 1)
        );
        backOff.setInitialInterval(initialBackoff.toMillis());
        backOff.setMaxInterval(maxBackoff.toMillis());
        backOff.setMultiplier(2);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                meterRegistry.counter("pulseops.kafka.retries", "topic", record.topic()).increment();
            }
        });
        return errorHandler;
    }

    private TopicPartition deadLetterDestination(
            ConsumerRecord<?, ?> record,
            Map<String, String> deadLetterTopics,
            MeterRegistry meterRegistry
    ) {
        String topic = deadLetterTopics.getOrDefault(record.topic(), record.topic() + ".dlq");
        meterRegistry.counter("pulseops.kafka.dlq.recoveries", "topic", record.topic()).increment();
        return new TopicPartition(topic, record.partition());
    }

    static final class DeadLetterTransport implements DisposableBean {
        private final DefaultKafkaProducerFactory<String, String> producerFactory;
        private final KafkaTemplate<String, String> template;

        DeadLetterTransport(DefaultKafkaProducerFactory<String, String> producerFactory) {
            this.producerFactory = producerFactory;
            this.template = new KafkaTemplate<>(producerFactory);
        }

        KafkaTemplate<String, String> template() {
            return template;
        }

        @Override
        public void destroy() {
            producerFactory.destroy();
        }
    }
}
