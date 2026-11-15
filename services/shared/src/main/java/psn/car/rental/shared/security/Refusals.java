package psn.car.rental.shared.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Writes a refused request in the error format of all services, without a JSON library. */
final class Refusals {

  private Refusals() {}

  static void notSignedIn(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    write(
        response,
        HttpServletResponse.SC_UNAUTHORIZED,
        "not-signed-in",
        "Not signed in",
        "The request needs a valid access token.");
  }

  static void forbidden(HttpServletResponse response, String detail) throws IOException {
    write(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "Not allowed", detail);
  }

  private static void write(
      HttpServletResponse response, int status, String type, String title, String detail)
      throws IOException {
    String body =
        "{\"type\":\"/problems/%s\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\"}"
            .formatted(type, title, status, escape(detail));
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
  }

  private static String escape(String text) {
    StringBuilder out = new StringBuilder();
    for (char c : text.toCharArray()) {
      if (c == '"' || c == '\\') {
        out.append('\\').append(c);
      } else if (c < 0x20) {
        out.append(String.format("\\u%04x", (int) c));
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
