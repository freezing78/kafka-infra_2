## Развертывание кластера
- В Portainer docker-compose.yml (Web-интерфейс для управления Docker Compose) Развертывание на машине с IP 10.127.1.2  
в папке /opt/ создается структура папок и файлов как приведена в проекте  
- Приложение запускается с машины IP 10.127.1.4 в IntelliJ IDEA  
----------------
##  АСТРОЙКА СТЭКА  
#Зайти в консоль контейнера в kafka conect и выполните команды
yum update -y  
yum install -y wget tar unzip  
yum install -y wget which tar  

#Создайте директорию для плагинов  
mkdir -p /usr/share/java/debezium-connector-postgresql  
cd /usr/share/java  

#Копируйте архив в контейнер (подставьте правильное имя контейнера)
docker cp /tmp/debezium-connector-postgres-3.3.2.Final-plugin.tar.gz kafka-infra-kafka-connect-1:/tmp/debezium.tar.gz  

#Распакуйте (предполагая, что файл уже скопирован)
tar -xzf /tmp/debezium.tar.gz -C /usr/share/java/debezium-connector-postgresql --strip-components=1  

#В контейнере установите JDBC драйвер  
bash -c "  
    # Скачайте PostgreSQL JDBC драйвер  
    echo 'Скачивание PostgreSQL JDBC драйвера...'  
    wget -q https://jdbc.postgresql.org/download/postgresql-42.7.3.jar -O /usr/share/java/postgresql-42.7.3.jar || \  
    curl -s -L https://jdbc.postgresql.org/download/postgresql-42.7.3.jar -o /usr/share/java/postgresql-42.7.3.jar || \  
    echo 'Не удалось скачать JDBC драйвер, попробуйте вручную'  
"  

#Проверьте что установилось  
bash -c "  
    echo '=== Проверка установки ==='  
    echo '1. Файлы Debezium:'  
    ls -la /usr/share/java/debezium-connector-postgresql/*.jar | head -10  
    echo ''  
    echo '2. JDBC драйвер:'  
    ls -la /usr/share/java/postgresql*.jar 2>/dev/null || echo 'JDBC драйвер не найден'  
    echo ''  
    echo '3. CONNECT_PLUGIN_PATH:'  
    echo \$CONNECT_PLUGIN_PATH  
"  

#Проверьте в контейнере kafka-infra-kafka-connect-1cd  
curl -s http://localhost:8083/connector-plugins | grep -i debezium  

#Или более подробно:  
bash -c '  
    curl -s http://localhost:8083/connector-plugins | python3 -c "  
import json, sys  
data = json.load(sys.stdin)  
debezium = [p for p in data if \"postgresql\" in p[\"class\"].lower()]  
if debezium:  
    print(\"✅ Debezium PostgreSQL Connector установлен!\")  
    print(f\"   Класс: {debezium[0][\"class\"]}\")  
    print(f\"   Версия: {debezium[0][\"version\"]}\")  
else:  
    print(\"❌ Debezium не найден. Все плагины:\")  
    for p in data:  
        print(f\"   - {p[\"class\"]}\")  
"  
'  




# Создайте конфиг файл
cat > /tmp/debezium-final.json << 'EOF'
{
  "name": "debezium-customers-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "postgres-user",
    "database.password": "postgres-pw",
    "database.dbname": "customers",
    
    "topic.prefix": "customers-db",
    
    "table.include.list": "public.users,public.orders",
    "schema.include.list": "public",
    
    "plugin.name": "pgoutput",
    "slot.name": "debezium_slot",
    "publication.name": "debezium_pub",
    "publication.autocreate.mode": "filtered",
    
    "snapshot.mode": "initial",
    "snapshot.locking.mode": "none",
    
    "heartbeat.interval.ms": "5000",
    "heartbeat.topics.prefix": "__debezium_heartbeat",
    
    "decimal.handling.mode": "double",
    "time.precision.mode": "adaptive",
    "tombstones.on.delete": "true",
    
    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://schema-registry:8081",
    "value.converter.schema.registry.url": "http://schema-registry:8081",
    
    "include.schema.changes": "false",
    "provide.transaction.metadata": "false"
  }
}
EOF

# Создайте коннектор
curl -X POST -H "Content-Type: application/json" --data @/tmp/debezium-final.json http://localhost:8083/connectors

# Проверьте
curl http://localhost:8083/connectors


#измените конфигурационный файл postgres напрямую из контейнера
# Найдите конфигурационный файл
docker exec kafka-infra-postgres-1 find / -name "postgresql.conf" -type f 2>/dev/null

# Обычное расположение в Alpine PostgreSQL
docker exec kafka-infra-postgres-1 ls -la /var/lib/postgresql/data/

# Добавьте настройки в конец файла
docker exec kafka-infra-postgres-1 sh -c "
    echo '' >> /var/lib/postgresql/data/postgresql.conf
    echo '# Debezium Logical Replication Settings' >> /var/lib/postgresql/data/postgresql.conf
    echo 'wal_level = logical' >> /var/lib/postgresql/data/postgresql.conf
    echo 'max_wal_senders = 10' >> /var/lib/postgresql/data/postgresql.conf
    echo 'max_replication_slots = 10' >> /var/lib/postgresql/data/postgresql.conf
    echo 'wal_keep_size = 1GB' >> /var/lib/postgresql/data/postgresql.conf
"

# Проверьте что добавилось
docker exec kafka-infra-postgres-1 tail -10 /var/lib/postgresql/data/postgresql.conf

#перегрузить контейнер

psql -U postgres-user -d customers -c "SELECT pg_drop_replication_slot('debezium_slot');" 2>/dev/null || echo "Слот уже удален или не существует"

#Теперь при записи данных в таблицу Базы данных создадутся топики
-Топик customers-db.public.users - сообщение (данные таблицы users)
-Топик customers-db.public.orders - сообщение (данные таблицы orders)
- Топик __debezium-heartbeat.customers-db - сообщения (heartbeat работает)
- Debezium Connector в состоянии RUNNING

Итоговая конфигурация:

Источник: PostgreSQL база customers
Таблицы: users и orders (только эти!)
Формат: Avro с Schema Registry
Топики: customers-db.public.users, customers-db.public.orders
Режим: Snapshot + Real-time изменения

Теперь у вас работает полный CDC (Change Data Capture) пайплайн:
Изменения в PostgreSQL → Debezium → Kafka → Потребители
Все операции (INSERT, UPDATE, DELETE) отслеживаются
Данные в формате Avro с контролем схемы

## Задание 2
Создайте/проверьте файл /opt/infra_template/prometheus/prometheus.yml
Создайте /opt/infra_template/kafka-connect/kafka-connect.yml для Debezium
Создайте /opt/infra_template/prometheus/kafka_alerts.yml
Создайте /opt/infra_template/prometheus/debezium_alerts.yml
sudo mkdir -p /opt/infra_template/prometheus/rules


# Создаем datasource конфигурацию
sudo tee /opt/infra_template/grafana/provisioning/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
EOF

# Создаем dashboards конфигурацию
sudo tee /opt/infra_template/grafana/provisioning/dashboards/dashboards.yml << 'EOF'
apiVersion: 1

providers:
  - name: 'default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    options:
      path: /etc/grafana/dashboards
EOF

# Создадим дашборд для Kafka Connect
sudo tee /opt/infra_template/grafana/dashboards/kafka-connect.json << 'EOF'
{
  "dashboard": {
    "title": "Kafka Connect Monitoring",
    "description": "Мониторинг Debezium PostgreSQL Connector",
    "tags": ["kafka", "connect", "debezium"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Состояние Kafka Connect",
        "type": "stat",
        "targets": [{
          "expr": "up{job=\"kafka-connect\"}",
          "legendFormat": "{{instance}}",
          "refId": "A"
        }],
        "fieldConfig": {
          "defaults": {
            "thresholds": {
              "steps": [
                {"value": 0, "color": "red"},
                {"value": 1, "color": "green"}
              ]
            },
            "mappings": [
              {"value": 0, "text": "OFFLINE"},
              {"value": 1, "text": "ONLINE"}
            ],
            "unit": "short"
          }
        },
        "gridPos": {"h": 8, "w": 12, "x": 0, "y": 0}
      },
      {
        "id": 2,
        "title": "Общее количество записей",
        "type": "graph",
        "datasource": "Prometheus",
        "targets": [{
          "expr": "sum(rate(kafka_connect_source_record_write_total[5m]))",
          "legendFormat": "Всего записей/сек",
          "refId": "A"
        }],
        "gridPos": {"h": 8, "w": 12, "x": 12, "y": 0}
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "10s"
  },
  "overwrite": true
}
EOF

# Создаем /opt/infra_template/grafana/Dockerfile
FROM grafana/grafana:8.1.6
# Установка плагинов
RUN grafana-cli plugins install grafana-piechart-panel
# Копирование конфигурационных файлов
COPY ./config.ini /etc/grafana/config.ini
COPY ./provisioning /etc/grafana/provisioning
# Создание директории для дашбордов и копирование
COPY ./dashboards /var/lib/grafana/dashboards/
# Образ уже имеет правильные права по умолчанию

# Создадим конфигурационный файл для JMX
sudo tee /opt/infra_template/kafka-connect/kafka-connect.yml << 'EOF'
lowercaseOutputName: true
lowercaseOutputLabelNames: true
whitelistObjectNames:
  - "kafka.connect:*"
  - "kafka.consumer:*"
  - "kafka.producer:*"
  - "com.automation.vertica.kafka.connect:*"

rules:
  # Основные метрики Kafka Connect
  - pattern: "kafka.connect<type=connect-worker-metrics><>(.+)"
    name: "kafka_connect_worker_$1"
    type: GAUGE
  
  - pattern: 'kafka.connect<type=connector-task-metrics, connector=(.+), task=(.+)><>(.+)'
    name: "kafka_connect_connector_$3"
    labels:
      connector: "$1"
      task: "$2"
    type: GAUGE
  
  - pattern: 'kafka.connect<type=source-task-metrics, connector=(.+), task=(.+)><>(.+)'
    name: "kafka_connect_source_$3"
    labels:
      connector: "$1"
      task: "$2"
    type: GAUGE
  
  - pattern: 'kafka.connect<type=sink-task-metrics, connector=(.+), task=(.+)><>(.+)'
    name: "kafka_connect_sink_$3"
    labels:
      connector: "$1"
      task: "$2"
    type: GAUGE
  
  - pattern: 'kafka.connect<type=connect-worker-rebalance-metrics><>(.+)'
    name: "kafka_connect_rebalance_$1"
    type: GAUGE
  
  # Дебаг - захватываем все остальные метрики
  - pattern: '.*'
EOF

# Скачаем jmx_prometheus_javaagent
cd /opt/infra_template/kafka-connect
sudo wget https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/0.17.2/jmx_prometheus_javaagent-0.17.2.jar

# Создаем/обновляем Dockerfile для Kafka Connect
sudo tee /opt/infra_template/kafka-connect/Dockerfile << 'EOF'
FROM confluentinc/cp-kafka-connect:7.5.1

# Устанавливаем JMX Prometheus агент
COPY jmx_prometheus_javaagent-0.17.2.jar /opt/jmx_prometheus_javaagent-0.17.2.jar
COPY kafka-connect.yml /etc/kafka-connect/kafka-connect.yml

# Устанавливаем Debezium PostgreSQL connector
RUN confluent-hub install --no-prompt debezium/debezium-connector-postgresql:2.5.0

# Копируем дополнительные коннекторы если есть
COPY confluent-hub-components/ /etc/kafka-connect/jars/

# Экспортируем порт для JMX
EXPOSE 9875 9876
EOF



