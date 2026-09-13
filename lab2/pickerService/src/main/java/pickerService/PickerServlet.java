package pickerService;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class PickerServlet extends HttpServlet {
  String KAFKA_BOOTSTRAP_SERVERS;
  String KAFKA_TOPIC;
  String KAFKA_GROUP_ID;
  String INSTANCE_NAME;
  Properties properties = new Properties();
  KafkaConsumer<String, String> consumer;
  final CopyOnWriteArrayList<String> processed = new CopyOnWriteArrayList<>();

  @Override
  public void init() throws ServletException {
    super.init();

    KAFKA_BOOTSTRAP_SERVERS = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
    if (KAFKA_BOOTSTRAP_SERVERS == null) {
      KAFKA_BOOTSTRAP_SERVERS = "localhost:9092";
    }

    KAFKA_TOPIC = System.getenv("KAFKA_TOPIC");
    if (KAFKA_TOPIC == null) {
      KAFKA_TOPIC = "orders";
    }

    KAFKA_GROUP_ID = System.getenv("KAFKA_GROUP_ID");
    if (KAFKA_GROUP_ID == null) {
      KAFKA_GROUP_ID = "picker-group";
    }

    INSTANCE_NAME = System.getenv("INSTANCE_NAME");
    if (INSTANCE_NAME == null) {
      INSTANCE_NAME = "picker-1";
    }

    properties.setProperty("group.id", KAFKA_GROUP_ID);
    properties.setProperty("auto.offset.reset", "earliest");
    properties.setProperty("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
    properties.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    properties.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
    consumer = new KafkaConsumer<>(properties);
    consumer.subscribe(List.of(KAFKA_TOPIC));

    Thread consumerThread = new Thread(() -> {
      while (true) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        for (ConsumerRecord<String, String> record : records) {
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            /* IGNORING */
          }
          processed.add(record.partition() + " " +
              record.offset() + " " + record.key() + " " +
              record.value() + " " + INSTANCE_NAME);
        }
      }
    });
    consumerThread.setDaemon(true);
    consumerThread.start();
  }

  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/html;charset=UTF-8");
    final StringBuilder sb = new StringBuilder();
    sb.append("<html><body><h1>" + INSTANCE_NAME + "</h1> <br> <ul>");

    for (final String entry : processed) {
      sb.append("<li>" + entry + "</li>");
    }

    sb.append("</ul></body></html>");

    response.getWriter().write(sb.toString());
  }
}
