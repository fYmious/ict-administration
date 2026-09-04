package lab1;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * DelayServlet
 */
public class DelayServlet extends HttpServlet {

  private final Tracer tracer = GlobalOpenTelemetry.getTracer("lab1.delay-servlet");

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    Span span = tracer.spanBuilder("slow-dependency").startSpan();

    try (Scope scope = span.makeCurrent()) {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      span.end();
    }

    File file = new File(getServletContext().getRealPath("delay.html"));
    if (file.isFile()) {
      response.setContentType(getServletContext().getMimeType(file.getAbsolutePath()));
      ServletOutputStream outputStream = response.getOutputStream();
      Files.copy(file.toPath(), outputStream);
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }
}
