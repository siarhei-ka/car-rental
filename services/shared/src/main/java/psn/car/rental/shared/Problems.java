package psn.car.rental.shared;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * The error format of all services: Problem Details (RFC 9457, NFR-22) with a type of the form
 * {@code /problems/<name>}.
 */
public final class Problems {

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, String type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("/problems/" + type));
    problem.setTitle(title);
    return problem;
  }
}
