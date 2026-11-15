package psn.car.rental.insurance.domain;

import java.util.regex.Pattern;

/**
 * An insurance option that every reservation in every country can choose, such as basic or full
 * cover. Its price comes from the insurance provider.
 *
 * @param preselected whether the UI chooses this option until the customer picks another one
 */
public record InsuranceOption(
    String code, String name, String description, boolean preselected, int sortOrder) {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public InsuranceOption {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new InvalidInsuranceException("An insurance code is an upper-case code such as BASIC");
    }
    if (name == null || name.isBlank()) {
      throw new InvalidInsuranceException("An insurance option has a name");
    }
    if (description == null) {
      description = "";
    }
  }
}
