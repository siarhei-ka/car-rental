package psn.car.rental.cacheupdater;

import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Loads the snapshot from the administration service. */
@Component
class AdministrationClient implements SnapshotSource {

  private final RestClient http;

  AdministrationClient(RestClient.Builder builder, CacheUpdaterProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.administrationTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.administrationTimeout());
    this.http =
        builder
            .baseUrl(properties.administrationUrl().toString())
            .requestFactory(requestFactory)
            .build();
  }

  @Override
  public ReferenceCache.Snapshot load() {
    return http.get().uri("/api/v1/reference-data").retrieve().body(ReferenceCache.Snapshot.class);
  }
}
