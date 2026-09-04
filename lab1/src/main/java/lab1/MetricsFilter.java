package lab1;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.opentelemetry.api.trace.Span;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * MetricsFilter
 */
public class MetricsFilter implements Filter {

  private final static Logger log = LoggerFactory.getLogger(MetricsFilter.class);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;

    String httpMethod = httpRequest.getMethod();
    String path = httpRequest.getRequestURI();
    String trace_id = Span.current().getSpanContext().getTraceId();

    MDC.put("trace_id", trace_id);

    long startTimer = System.nanoTime();
    try {
      log.info("Incoming request {} {}", httpMethod, path);

      chain.doFilter(request, response);
    } finally {
      double durationInSeconds = (System.nanoTime() - startTimer) / 1_000_000_000.0;
      int httpStatus = httpResponse.getStatus();
      String statusStr = String.valueOf(httpStatus);

      MetricsUtil.REQUESTS_TOTAL.labels(httpMethod, path, statusStr).inc();
      MetricsUtil.REQUEST_DURATION.labels(httpMethod, path, statusStr).observe(durationInSeconds);

      if (httpStatus > 500) {
        MetricsUtil.ERRORS_TOTAL.labels(httpMethod, path, statusStr).inc();
        log.error("Request failed {} {} status={}", httpMethod, path, httpStatus);
      } else {
        log.info("Request completed {} {} status={}", httpMethod, path, httpStatus);
      }

      MDC.remove("trace_id");
    }
  }

}
