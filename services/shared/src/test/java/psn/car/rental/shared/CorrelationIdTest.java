package psn.car.rental.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CorrelationIdTest {

  @AfterEach
  void clearLoggingContext() {
    MDC.clear();
  }

  @Test
  void keepsAValidIdFromTheCallerDuringTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationId.HEADER, "abc-123");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seen = new AtomicReference<>();

    new CorrelationIdFilter()
        .doFilter(
            request,
            response,
            new MockFilterChain() {
              @Override
              public void doFilter(
                  jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(CorrelationId.current().orElse(null));
              }
            });

    assertThat(seen.get()).isEqualTo("abc-123");
    assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("abc-123");
    assertThat(CorrelationId.current()).isEmpty();
  }

  @Test
  void replacesAMissingOrUnsafeIdWithANewOne() {
    assertThat(CorrelationId.acceptOrCreate(null)).hasSize(36);
    assertThat(CorrelationId.acceptOrCreate("bad id\nwith newline")).hasSize(36);
    assertThat(CorrelationId.acceptOrCreate("x".repeat(65))).hasSize(36);
  }

  @Test
  void passesTheIdOnInOutgoingCalls() {
    RestClient.Builder builder = RestClient.builder();
    new SharedWebAutoConfiguration.RestClientCorrelation()
        .correlationIdRestClientCustomizer()
        .customize(builder);
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://inventory/ping"))
        .andExpect(header(CorrelationId.HEADER, "abc-123"))
        .andRespond(withSuccess());
    MDC.put(CorrelationId.MDC_KEY, "abc-123");

    builder.build().get().uri(URI.create("http://inventory/ping")).retrieve().toBodilessEntity();

    server.verify();
  }

  @Test
  void buildsProblemDetails() {
    ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "no-car-available", "Title", "Text");

    assertThat(problem.getType()).isEqualTo(URI.create("/problems/no-car-available"));
    assertThat(problem.getStatus()).isEqualTo(409);
    assertThat(problem.getTitle()).isEqualTo("Title");
    assertThat(problem.getDetail()).isEqualTo("Text");
  }
}
