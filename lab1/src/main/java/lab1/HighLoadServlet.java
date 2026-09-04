package lab1;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HighLoadServlet
 */
public class HighLoadServlet extends HttpServlet {

  private final static Logger log = LoggerFactory.getLogger(MetricsFilter.class);

  private static final int LOAD_NUMBER = 1000;
  private final HttpClient client = HttpClient.newHttpClient();
  private final String BASE_URL = "http://localhost:8080";

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {

    for (int i = 0; i < LOAD_NUMBER; i++) {
      final HttpRequest req = HttpRequest.newBuilder().uri(URI.create(BASE_URL + "/")).GET().build();
      try {
        client.send(req, HttpResponse.BodyHandlers.discarding());
      } catch (IOException | InterruptedException e) {
        System.err.println("Error: " + e);
        log.error("highLoad request failed", e);
      }
    }

    final File file = new File(getServletContext().getRealPath("highLoad.html"));
    if (file.isFile()) {
      final ServletOutputStream outputStream = response.getOutputStream();
      Files.copy(file.toPath(), outputStream);
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

}
