package psn.car.rental.info.cartype;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Car types and car classes, for anyone. Only active items are listed. */
@RestController
class CarTypeController {

  private static final String CODE = "[A-Z][A-Z0-9_]{0,31}";

  private final ReferenceReader reader;

  CarTypeController(ReferenceReader reader) {
    this.reader = reader;
  }

  @GetMapping("/api/v1/car-types")
  List<Map<String, Object>> carTypes() {
    return reader.all("ref:car-types", "code").stream().filter(ReferenceReader::active).toList();
  }

  @GetMapping("/api/v1/car-types/{code}")
  Map<String, Object> carType(@PathVariable @Pattern(regexp = CODE) String code) {
    return reader
        .one("ref:car-types", code)
        .orElseThrow(() -> new NotFoundException("Car type " + code + " does not exist"));
  }

  @GetMapping("/api/v1/car-classes")
  List<Map<String, Object>> carClasses() {
    return reader.all("ref:car-classes", "code").stream().filter(ReferenceReader::active).toList();
  }
}
