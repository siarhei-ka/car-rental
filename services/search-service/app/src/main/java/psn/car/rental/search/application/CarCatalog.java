package psn.car.rental.search.application;

import java.util.List;
import psn.car.rental.search.domain.CarKind;

/** The car type info service. */
public interface CarCatalog {

  List<CarKind> carTypes();

  List<CarKind> carClasses();
}
