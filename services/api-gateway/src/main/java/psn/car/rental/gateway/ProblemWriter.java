package psn.car.rental.gateway;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import psn.car.rental.shared.Problems;
import tools.jackson.databind.json.JsonMapper;

/** Writes the refusals of the gateway in the error format of all services. */
@Component
class ProblemWriter {

  private final JsonMapper json;

  ProblemWriter(JsonMapper json) {
    this.json = json;
  }

  static ProblemDetail notSignedIn() {
    return Problems.of(
        HttpStatus.UNAUTHORIZED, "not-signed-in", "Not signed in", "Sign in to use this function.");
  }

  static ProblemDetail forbidden(String detail) {
    return Problems.of(HttpStatus.FORBIDDEN, "forbidden", "Not allowed", detail);
  }

  void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
