package psn.car.rental.insurance.application;

import java.util.List;
import psn.car.rental.insurance.domain.InsuranceOption;

/** Where the insurance options are kept. They are the same in every country. */
public interface OptionStore {

  /** The options in the order the UI shows them. */
  List<InsuranceOption> all();
}
