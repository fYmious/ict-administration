package lab1;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

/**
 * MetricsUtil
 */
public final class MetricsUtil {

  public static final Counter REQUESTS_TOTAL = Counter.build()
      .name("number_of_requests_total")
      .help("Total number of all requests")
      .labelNames("method", "path", "status")
      .register();

  public static final Counter ERRORS_TOTAL = Counter.build()
      .name("number_of_error_total")
      .help("Total number of all errors")
      .labelNames("method", "path", "status")
      .register();

  public static final Histogram REQUEST_DURATION = Histogram.build()
      .name("request_duration_seconds")
      .help("Request duration in seconds")
      .labelNames("method", "path", "status")
      .register();

  // Utililty class
  private MetricsUtil() {

  }

}
