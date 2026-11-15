package psn.car.rental.insurance.persistence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.insurance.application.OptionStore;
import psn.car.rental.insurance.domain.InsuranceOption;

@Repository
class JdbcOptionStore implements OptionStore {

  private final JdbcClient jdbc;

  JdbcOptionStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<InsuranceOption> all() {
    return jdbc.sql(
            "select code, name, description, preselected, sort_order from insurance_option"
                + " order by sort_order, code")
        .query((row, n) ->
            new InsuranceOption(
                row.getString("code"),
                row.getString("name"),
                row.getString("description"),
                row.getBoolean("preselected"),
                row.getInt("sort_order")))
        .list();
  }
}
