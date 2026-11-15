package psn.car.rental.shared;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The correlation ID of the current request (NFR-19). It is kept in the logging context, so that
 * every log line carries it, and it is passed on to other services.
 */
public final class CorrelationId {

  /** The HTTP header that carries the correlation ID. */
  public static final String HEADER = "X-Correlation-Id";

  /** The key of the correlation ID in the logging context. */
  public static final String MDC_KEY = "correlationId";

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private CorrelationId() {}

  /** The correlation ID of the current thread, if a request is being handled. */
  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY));
  }

  /** The received ID if it is safe to log, or a new one. */
  static String acceptOrCreate(String received) {
    return received != null && VALID.matcher(received).matches()
        ? received
        : UUID.randomUUID().toString();
  }
}
