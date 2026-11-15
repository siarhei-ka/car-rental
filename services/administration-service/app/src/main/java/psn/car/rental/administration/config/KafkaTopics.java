package psn.car.rental.administration.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Creates the topics this service publishes to, if they do not exist. */
@Configuration(proxyBeanMethods = false)
class KafkaTopics {

  @Bean
  NewTopic referenceDataTopic(AdministrationProperties properties) {
    return TopicBuilder.name(properties.referenceDataTopic()).partitions(3).replicas(1).build();
  }
}
