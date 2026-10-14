import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ReadPreference;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@WebServlet("/api/*")
public class NotesServlet extends HttpServlet {
  private static final Gson GSON = new Gson();

  private MongoClient writeClient;
  private MongoClient readClient;
  private MongoCollection<Document> writeCollection;
  private MongoCollection<Document> readCollection;

  @Override
  public void init() {
    String primaryUri = System.getenv().getOrDefault(
        "MONGO_PRIMARY_URI",
        "mongodb://mongo1:27017/?replicaSet=rs0");

    String replicaUri = System.getenv().getOrDefault(
        "MONGO_REPLICA_URI",
        "mongodb://mongo1:27017,mongo2:27017,mongo3:27017/?replicaSet=rs0");

    writeClient = MongoClients.create(primaryUri);

    MongoClientSettings readSettings = MongoClientSettings.builder()
        .applyConnectionString(new ConnectionString(replicaUri))
        // .readPreference(ReadPreference.secondary())
        .readPreference(ReadPreference.valueOf(
            System.getenv().getOrDefault("READ_PREFERENCE", "secondary")))
        .applyToSocketSettings(builder -> builder.connectTimeout(3, TimeUnit.SECONDS))
        .build();

    readClient = MongoClients.create(readSettings);

    MongoDatabase writeDb = writeClient.getDatabase("lab4");
    MongoDatabase readDb = readClient.getDatabase("lab4");

    writeCollection = writeDb.getCollection("notes");
    readCollection = readDb.getCollection("notes");
  }

  @Override
  protected void doGet(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    try {
      if ("/notes".equals(request.getPathInfo())) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();

        String source;
        try (MongoCursor<Document> cursor = readCollection.find()
            .sort(new Document("createdAt", -1))
            .limit(200)
            .iterator()) {
          source = cursor.getServerAddress().toString();
          while (cursor.hasNext()) {
            Document d = cursor.next();
            result.add(Map.of(
                "id", d.getObjectId("_id").toHexString(),
                "text", d.getString("text"),
                "createdAt", d.getDate("createdAt").toInstant().toString()));
          }
        }
        response.setHeader("X-Served-By", source);

        response.getWriter().write(GSON.toJson(result));
        return;
      }

      response.sendError(HttpServletResponse.SC_NOT_FOUND);
    } catch (com.mongodb.MongoException e) {
      sendError503(response, "Чтение недоступно: " + e.getMessage());
    }
  }

  @Override
  protected void doPost(
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    String path = request.getPathInfo();

    if ("/notes".equals(path)) {
      Document note = new Document("text", "Заметка " + new Date())
          .append("createdAt", new Date());

      writeCollection.insertOne(note);

      response.setStatus(HttpServletResponse.SC_CREATED);
      response.getWriter().write(GSON.toJson(
          Map.of("ok", true, "id", note.getObjectId("_id").toHexString())));
      return;
    }

    if ("/load".equals(path)) {
      int count = 500;
      String batch = String.valueOf(System.currentTimeMillis() % 100000);

      for (int i = 0; i < count; i++) {
        Document note = new Document("text", "high load " + batch + " #" + i)
            .append("createdAt", new Date());

        writeCollection.insertOne(note);
      }

      response.getWriter().write(GSON.toJson(
          Map.of("ok", true, "created", count, "batch", batch)));
      return;
    }

    response.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  private void sendError503(HttpServletResponse response, String message)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write(GSON.toJson(Map.of("message", message)));
  }

  @Override
  public void destroy() {
    if (writeClient != null) {
      writeClient.close();
    }
    if (readClient != null) {
      readClient.close();
    }
  }
}
