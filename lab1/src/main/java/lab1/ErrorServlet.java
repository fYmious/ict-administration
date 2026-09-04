package lab1;

import java.io.IOException;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ErrorServlet
 */
public class ErrorServlet extends HttpServlet {
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Span.current().setStatus(StatusCode.ERROR, "502");
    response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
  }
}
