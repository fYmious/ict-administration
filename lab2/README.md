# Лабораторная работа №2 — Kafka для двух сервисов

> [!IMPORTANT]
> Запуск всего стенда:
> ```bash
> docker compose up --build
> ```

## Часть 1. Сервисы

Оба сервиса созданы из [Maven Webapp Archetype](https://maven.apache.org/archetypes/maven-archetype-webapp/):
- [Picker Service](pickerService) — [`PickerServlet`](pickerService/src/main/java/pickerService/PickerServlet.java)
- [User Service](userService) — [`UserServlet`](userService/src/main/java/userService/UserServlet.java)

Настройки вынесены в переменные окружения (при отсутствии берутся дефолты): `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_GROUP_ID`, `INSTANCE_NAME`.

### picker-service

В `init()` читаются переменные окружения, создаётся один `KafkaConsumer`, он подписывается на топик, и в фоновом потоке запускается цикл `poll()`. Каждая запись «собирается» (пауза 500 мс) и добавляется в `CopyOnWriteArrayList` в виде «partition offset key value instance».

```java
properties.setProperty("group.id", KAFKA_GROUP_ID);
properties.setProperty("auto.offset.reset", "earliest");
properties.setProperty("bootstrap.servers", KAFKA_BOOTSTRAP_SERVERS);
consumer = new KafkaConsumer<>(properties);
consumer.subscribe(List.of(KAFKA_TOPIC));

Thread consumerThread = new Thread(() -> {
  while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
    for (ConsumerRecord<String, String> record : records) {
      Thread.sleep(500);
      processed.add(record.partition() + " " + record.offset() + " "
          + record.key() + " " + record.value() + " " + INSTANCE_NAME);
    }
  }
});
consumerThread.setDaemon(true);
consumerThread.start();
```

`KafkaConsumer` не потокобезопасен, `poll()` должен вызываться регулярно одним и тем же потоком. Если бы чтение шло в `doGet()`, на каждый HTTP-запрос появлялся бы новый `poll()` или новый консьюмер, что ломает модель consumer-группы и вызывает постоянные ребалансировки. `doGet()` только читает готовую коллекцию и выводит её списком вместе с `INSTANCE_NAME`.

### user-service

В `init()` создаётся `KafkaProducer`. Страница [`userService.html`](userService/src/main/webapp/userService.html) отдаётся через `doGet`, кнопка «Создать заказ» вызывает `POST`, и на экран выводится `orderId`, partition и offset.

```java
String orderId = UUID.randomUUID().toString();
String value = "{\"orderId\":\"" + orderId + "\",\"items\":[\"item-A\",\"item-B\"]}";
ProducerRecord<String, String> record = new ProducerRecord<>(KAFKA_TOPIC, orderId, value);

RecordMetadata md = kafkaProducer.send(record).get();
```

`send()` асинхронный, а `.get()` блокирует поток до подтверждения записи, чтобы получить реальные `partition` и `offset`. Ключ сообщения выступает `orderId`, поэтому одинаковые ключи всегда попадают в одну партицию, которая выбирается по хэшу ключа.

### Dockerfile

Он одинаковый для обоих сервисов. Производится multi-stage сборка `.war` через `mvn clean package` и деплой в Tomcat.

```Dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM tomcat:10.1-jre21
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /app/target/*.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
CMD ["catalina.sh", "run"]
```

---

## Часть 2. Kafka и сервисы через docker-compose

Все работает на уровне контейнеров, `docker compose`. Kafka в режиме KRaft, без ZooKeeper, топик создаётся из конфигурации сервисом `kafka-init`.

```yml
services:
  kafka:
    image: apache/kafka:3.7.1
    environment:
      - KAFKA_NODE_ID=1
      - KAFKA_PROCESS_ROLES=broker,controller
      - KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:9093
      - KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
      - KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
      - KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
      - KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
    ports:
      - "9092:9092"

  kafka-init:
    image: apache/kafka:3.7.1
    depends_on:
      - kafka
    entrypoint: ["/bin/sh", "-c"]
    command:
      - |
        /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
          --topic orders --bootstrap-server kafka:9092 \
          --partitions 1 --replication-factor 1

  picker-service:
    build: ./pickerService/.
    ports: ["8081:8080"]
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - KAFKA_TOPIC=orders
      - KAFKA_GROUP_ID=picker-group
      - INSTANCE_NAME=picker-1

  user-service:
    build: ./userService/.
    ports: ["8082:8080"]
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - KAFKA_TOPIC=orders
```

`kafka-init` использует образ `apache/kafka`, чтобы получить `kafka-topics.sh`, выполняет команду один раз при старте и завершается. `depends_on` гарантирует только порядок запуска контейнеров, но не готовность брокера. В логах при первом старте видны предупреждения `Connection to node -1 ... could not be established`, но утилита сама повторяет попытки, и топик создаётся. `--if-not-exists` делает запуск идемпотентным.

![Превью](media/p2.gif)

---

## Часть 4. Kafka UI

Добавлен в тот же compose-файл:

```yml
  kafka-ui:
    image: provectuslabs/kafka-ui
    environment:
      - KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=kafka:9092
      - KAFKA_CLUSTERS_0_NAME=local
    ports:
      - "8080:8080"
```

Доступен на `http://localhost:8080`: кластер `local`, топик `orders`, его партиции и consumer-группа `picker-group`.

![Превью](media/p1.gif)

---

## Часть 3. Ситуации, решённые через конфигурацию

### Ситуация 1. Распродажа: партиции от 1 до 3 и обратно

Мы ждём всплеск заказов, и обрабатывать их должны несколько сборщиков параллельно. При 1 партиции в группе работает только один потребитель, остальные простаивают, потому что одну партицию в группе читает ровно один потребитель.

В `kafka-init` добавлена команда увеличения числа партиций. Так как флаг `--create --if-not-exists` не меняет существующий топик, поэтому нужен отдельный `--alter`:

```diff
 command:
   - |
     /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
       --topic orders --bootstrap-server kafka:9092 \
       --partitions 1 --replication-factor 1
+    /opt/kafka/bin/kafka-topics.sh --alter \
+      --topic orders --bootstrap-server kafka:9092 \
+      --partitions 3
```

Правка `docker-compose.yml` и `docker compose up -d`. Конфигурация идемпотентна, результат один и тот же при любом числе повторных запусков.

В Kafka UI у топика `orders` стало 3 партиции.

![Превью](media/2.png)

Лишние `picker-service` я удалил из `docker-compose.yml`, число обработчиков вернулось к прежнему. Число партиций уменьшить не удалось, так как это ограничение Kafka. Партиция является упорядоченным логом на диске, а партиция сообщения вычисляется по хэшу ключа от числа партиций. Если уменьшить число партиций, формула сломается для существующих ключей, а сообщения из удаляемых партиций стали бы недостижимы. Поэтому у Kafka нет операции уменьшения. Единственный путь, подразумевает создание нового топика с меньшим числом партиций и перенести данные.

```bash
$ docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --alter --topic orders --bootstrap-server kafka:9092 --partitions 1
ERROR org.apache.kafka.common.errors.InvalidPartitionsException: The topic orders currently has 3 partition(s); 1 would not be an increase.
```

---

### Ситуация 2. Второй сборщик

Нужно разделить нагрузку между несколькими экземплярами сборщика в одной группе и понять, что будет, если сборщиков больше, чем партиций.

Я добавил в `docker-compose.yml` экземпляры с тем же `KAFKA_GROUP_ID`, но разными `INSTANCE_NAME` и портами:

```yml
  picker-service-2:
    build: ./pickerService/.
    ports: ["8083:8080"]
    environment:
      - KAFKA_BOOTSTRAP_SERVERS=kafka:9092
      - KAFKA_TOPIC=orders
      - KAFKA_GROUP_ID=picker-group
      - INSTANCE_NAME=picker-2
  # picker-service-3 (порт 8084, picker-3) и picker-service-4 (порт 8085, picker-4) — по аналогии
```

По итогу получилась след. картна:

- Два сборщика (3 партиции): заказы разделились между `picker-1` и `picker-2`.

  ![Превью](media/p3.gif)

- 3 сборщика = 3 партиции: каждому достаётся своя партиция.

  ![Превью](media/p4.gif)

- 4 сборщика > 3 партициb: один экземпляр (`picker-2`) не получил ни одной партиции и простаивает, хотя штатно подключился к группе.

  ![Превью](media/p5.gif)

Одну партицию внутри группы читает не более одного потребителя, поэтому предел параллелизма группы равен числу партиций. Лишние потребители остаются в резерве, они опрашивают брокер, но получают пустые ответы. Если один из активных упадёт, произойдёт ребалансировка, и резервный получит освободившуюся партицию.

---

### Ситуация 3. Сборщик упал во время деплоя

Сборщик недоступен, а заказы продолжают поступать. Нужно убедиться, что они не потеряны.

Конфиг при этом не менялся. Остановка и запуск сервиса, то есть эксплуатация, а не настройка Kafka:

```bash
docker compose stop picker-service
# создание заказов... 
docker compose start picker-service
```

Заказы лежат в топике, а группа хранит только указатель, поэтому после возврата сборщик продолжит с того места, где остановился.

Пока сборщик остановлен, в Kafka UI в топике `orders` растёт Message Count, а у группы `picker-group` растёт lag, то есть сообщения есть, но они не прочтаны.

![Превью](media/p6.gif)

После запуска сначала идёт ребалансировка, потом сборщик вычитывает накопленное.

![Превью](media/3.png)

Все накопленные заказы появились на странице сборщика, и lag упал до 0. Ни один заказ не потерян.

![Превью](media/4.png)

Продюсеру не важно, есть ли потребители, он пишет в лог партиции. Kafka хранит сообщения до истечения retention независимо от того, прочитаны ли они, и работает как буфер между сервисами. В классической очереди сообщение удаляется после доставки.

---

## Часть 5. Ситуации, решённые через Kafka UI

### Ситуация 4. Разбор жалобы на заказ

По конкретному заказу жалоба. Нужно найти сообщение и понять, обработано ли оно.

Выбрал `orderId` в Kafka UI:

```
7a691bcf-62d7-4511-8a44-8548f481fd6c
```

Нашёл сообщение в топике `orders` через поиск по ключу `partition = 0`, `offset = 1`.

![Превью](media/5.png)

Обработано ли оно, видно по группе. committed offset группы равен 2, то есть больше offset'а сообщения (1), значит сообщение прочитано и обработано.

![Превью](media/6.png)

---

### Ситуация 5. Пересчёт истории

Аналитикам нужно заново прогнать все заказы с начала.

Я сначала остановил сборщик. Kafka UI не даёт менять committed offset у группы, пока в ней есть активный потребитель, который владеет партициями. Затем в Kafka UI для группы `picker-group` я выбрал Reset offsets, Earliest для топика `orders`.

![Превью](media/7.png)
![Превью](media/8.png)
![Превью](media/9.png)

После `docker compose start picker-service` произошла ребалансировка, и сборщик заново обработал все заказы с offset 0.

![Превью](media/10.png)
![Превью](media/11.png)

Kafka не удаляет сообщение после прочтения, оно лежит в логе до истечения retention. Группа хранит лишь указатель, сброс это просто перемещение указателя назад, данные при этом не пропадают. В классической очереди сообщение удаляется после подтверждения, истории нет, и перечитать нечего.

---

## Часть 3 (продолжение). Ситуация 6 — через конфиг

### Ситуация 6. Retention: диск заполняется старыми заказами

Заказы нужны пару дней, а они копятся бесконечно.

Retention задан на уровне брокера в `docker-compose.yml`, сервис `kafka`:

```diff
 kafka:
   environment:
     - KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1
+    - KAFKA_LOG_RETENTION_MS=60000
+    - KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS=10000
```

1. Создал заказы через `user-service`, в Kafka UI Message Count вырос.

   ![Превью](media/13.png)

2. Через время, большее `retention.ms`, Message Count стал нулевым, хотя `picker-group` эти сообщения уже прочитала.

   ![Превью](media/14.png)

Kafka чистит данные только по сроку или размеру, независимо от того, дочитал ли их кто-то. Несколько независимых групп читают с разной скоростью, а можно ещё и пересчитывать историю. Поэтому retention нужно выбирать не меньше окна, которое нужно самому медленному потребителю. Брокер раз в `log.retention.check.interval.ms` проверяет сегменты и удаляет просроченные.

---

## Часть 6. Мониторинг

### Экспорт метрик

Kafka отдаёт метрики только через JMX, поэтому подключён JMX Prometheus Exporter как Java-агент. Файлы [агента](monitoring/jmx_prometheus_javaagent.jar) и [конфигурации](monitoring/kafka-jmx-exporter.yml) смонтированы в контейнер `kafka`:

```yaml
environment:
  - KAFKA_JVM_PERFORMANCE_OPTS=-javaagent:/opt/jmx-exporter/jmx_prometheus_javaagent.jar=7071:/opt/jmx-exporter/kafka-jmx-exporter.yml
volumes:
  - ./monitoring/jmx_prometheus_javaagent.jar:/opt/jmx-exporter/jmx_prometheus_javaagent.jar
  - ./monitoring/kafka-jmx-exporter.yml:/opt/jmx-exporter/kafka-jmx-exporter.yml
ports:
  - "7071:7071"
```

Сначала агент подключался через `KAFKA_OPTS`, но официальный образ `apache/kafka` его не использует (скрипт `/etc/kafka/docker/launch` собирает JVM-опции из `KAFKA_JVM_PERFORMANCE_OPTS`). После замены переменной в командной строке Java-процесса появился `-javaagent:...`.

```yml
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml
    ports: ["9090:9090"]
    depends_on: [kafka]

  grafana:
    image: grafana/grafana:latest
    ports: ["3000:3000"]
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    depends_on: [prometheus]
```

```yaml
# monitoring/prometheus.yml
scrape_configs:
  - job_name: "kafka"
    static_configs:
      - targets: ["kafka:7071"]
```

### Дашборд

Метрики топика `orders`:
- Messages/sec in (`kafka_server_brokertopicmetrics_messagesinpersec_topic_orders_oneminuterate`) — скорость поступления заказов, нагрузка от `user-service`.
- Bytes in/sec (`..._bytesinpersec_topic_orders_oneminuterate`) — входящий трафик, нагрузка на диск и сеть.
- Failed produce requests/sec (`..._failedproducerequestspersec_oneminuterate`) — неудачные записи, индикатор проблем на брокере.

![Превью](media/15.png)

При серии заказов одновременно растут `Messages/sec` и `Bytes In/sec`, потом спадают. `Failed Producer Requests/sec` всё время равна нулю, записи проходили без ошибок.

### 3 метрики для алертов

1. `kafka_controller_kafkacontroller_activecontrollercount != 1`
   - Ловитотсутствие или дублирование активного контроллера.
   - Без контроллера кластер не может менять метаданные, а два контроллера это признак split-brain.

2. `kafka_server_replicamanager_underreplicatedpartitions > 0`
   - Ловит партиции, у которых реплик в ISR меньше replication factor.
   - Защита от потери данных не работает, и при падении лидера такой партиции данные можно потерять частично или полностью.
   - В этом демо-стенде один брокер, поэтому метрика всегда 0. Алерт приведён как правильный выбор для промышленного кластера из нескольких брокеров.

3. `rate(kafka_server_brokertopicmetrics_failedproducerequestspersec_count[5m]) > 0`
   - Ловит рост числа неудачных записей от продюсеров.
   - Бизнес-события не сохраняются в Kafka вообще, то есть это потерянные заказы, а не просто задержка обработки.
