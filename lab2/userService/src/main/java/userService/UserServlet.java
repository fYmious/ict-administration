package userService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class UserServlet extends HttpServlet {
  KafkaProducer<String, String> kafkaProducer;
  String KAFKA_BOOTSTRAP_SERVERS;
  String KAFKA_TOPIC;

  @Override
  public void init() throws ServletException {
    super.init();

    KAFKA_BOOTSTRAP_SERVERS = (System.getenv("KAFKA_BOOTSTRAP_SERVERS") == null) ? "localhost:9092"
        : System.getenv("KAFKA_BOOTSTRAP_SERVERS");

    KAFKA_TOPIC = (System.getenv("KAFKA_TOPIC") == null) ? "orders" : System.getenv("KAFKA_TOPIC");

    Properties properties = new Properties();
    properties.put("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
    properties.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    properties.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

    kafkaProducer = new KafkaProducer<>(properties);
  }

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {

    final File file = new File(getServletContext().getRealPath("userService.html"));
    if (file.isFile()) {
      final ServletOutputStream outputStream = response.getOutputStream();
      Files.copy(file.toPath(), outputStream);
    } else {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String orderId = UUID.randomUUID().toString();
    String value = "{\"orderId\":\"" + orderId + "\",\"items\":[\"item-A\",\"item-B\"]}";
    ProducerRecord<String, String> producerRecord = new ProducerRecord<String, String>(KAFKA_TOPIC, orderId, value);

    Future<RecordMetadata> send = kafkaProducer.send(producerRecord);
    try {
      RecordMetadata recordMetadata = send.get();
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write("{\"orderId\":\"" + orderId + "\",\"partition\":" + recordMetadata.partition()
          + ",\"offset\":" + recordMetadata.offset() + "}");
    } catch (InterruptedException | ExecutionException e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  @Override
  public void destroy() {
    if (kafkaProducer != null) {
      kafkaProducer.close();
    }
    super.destroy();
  }

}
