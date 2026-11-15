package psn.car.rental.info.location;

import jakarta.validation.constraints.Pattern;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Countries and locations, for anyone. Only active items are listed, because a customer can reserve
 * only at an active location.
 */
@RestController
class LocationController {

  private static final String COUNTRY = "[a-z]{2}";
  private static final String ID = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";

  private final ReferenceReader reader;

  LocationController(ReferenceReader reader) {
    this.reader = reader;
  }

  @GetMapping("/api/v1/countries")
  List<Map<String, Object>> countries() {
    return reader.all("ref:countries", "code").stream().filter(ReferenceReader::active).toList();
  }

  /** The active locations of one country, or of all active countries. */
  @GetMapping("/api/v1/locations")
  List<Map<String, Object>> locations(
      @RequestParam(required = false) @Pattern(regexp = COUNTRY) String country) {
    Stream<String> countries =
        country != null
            ? Stream.of(country)
            : countries().stream().map(c -> (String) c.get("code"));
    return countries
        .flatMap(code -> reader.all("ref:locations:" + code, "id").stream())
        .filter(ReferenceReader::active)
        .sorted(
            Comparator.comparing((Map<String, Object> l) -> String.valueOf(l.get("country")))
                .thenComparing(l -> String.valueOf(l.get("id"))))
        .toList();
  }

  /** One location, also when it is not active, so that existing reservations can show it. */
  @GetMapping("/api/v1/locations/{country}/{id}")
  Map<String, Object> location(
      @PathVariable @Pattern(regexp = COUNTRY) String country,
      @PathVariable @Pattern(regexp = ID) String id) {
    return reader
        .one("ref:locations:" + country, id)
        .orElseThrow(
            () -> new NotFoundException("Location " + id + " does not exist in " + country));
  }
}
