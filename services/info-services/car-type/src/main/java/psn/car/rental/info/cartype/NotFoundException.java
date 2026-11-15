package psn.car.rental.info.cartype;

/** The requested item does not exist. */
class NotFoundException extends RuntimeException {

  NotFoundException(String message) {
    super(message);
  }
}
