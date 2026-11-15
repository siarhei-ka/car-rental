package psn.car.rental.shared.events;

import java.time.Clock;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The outbox and analytics events.
 *
 * <ul>
 *   <li>{@code car-rental.outbox.enabled=true}: events go through the outbox table named by {@code
 *       car-rental.outbox.table}, which the service creates with its own migration.
 *   <li>{@code car-rental.analytics.direct=true}: a service without a database sends analytics
 *       events straight to Kafka, without waiting.
 * </ul>
 */
@AutoConfiguration(
    afterName = {
      "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
      "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
      "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
      "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
      "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
    })
@ConditionalOnClass(KafkaTemplate.class)
public class EventsAutoConfiguration {

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(JdbcClient.class)
  @ConditionalOnProperty(name = "car-rental.outbox.enabled", havingValue = "true")
  @EnableScheduling
  static class OutboxConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Outbox outbox(
        JdbcClient jdbc,
        JsonMapper json,
        Clock clock,
        @Value("${car-rental.outbox.table:outbox}") String table) {
      return new JdbcOutbox(jdbc, json, clock, table);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(
        JdbcClient jdbc,
        KafkaTemplate<String, String> kafka,
        TransactionTemplate transactions,
        Clock clock,
        @Value("${car-rental.outbox.table:outbox}") String table) {
      return new OutboxPublisher(jdbc, kafka, transactions, clock, table);
    }

    @Bean
    @ConditionalOnMissingBean
    Analytics analytics(
        Outbox outbox, Clock clock, @Value("${spring.application.name}") String service) {
      return new OutboxAnalytics(outbox, clock, service);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = "car-rental.analytics.direct", havingValue = "true")
  static class DirectConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Analytics analytics(
        KafkaTemplate<String, String> kafka,
        JsonMapper json,
        Clock clock,
        @Value("${spring.application.name}") String service) {
      return new DirectAnalytics(
          kafka, json, clock, service, Executors.newVirtualThreadPerTaskExecutor());
    }
  }
}
