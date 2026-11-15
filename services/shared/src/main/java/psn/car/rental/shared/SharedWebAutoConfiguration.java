package psn.car.rental.shared;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/** Registers the correlation ID filter, and passes the correlation ID on in outgoing calls. */
@AutoConfiguration
public class SharedWebAutoConfiguration {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  CorrelationIdFilter correlationIdFilter() {
    return new CorrelationIdFilter();
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(RestClientCustomizer.class)
  static class RestClientCorrelation {

    @Bean
    RestClientCustomizer correlationIdRestClientCustomizer() {
      return builder ->
          builder.requestInterceptor((request, body, execution) -> {
            CorrelationId.current()
                .ifPresent(id -> request.getHeaders().set(CorrelationId.HEADER, id));
            return execution.execute(request, body);
          });
    }
  }
}
