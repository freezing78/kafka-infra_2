package com.example;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaAvroConsumer {

    private final KafkaConsumer<String, GenericRecord> consumer;
    private final ObjectMapper objectMapper;
    private final String topic;

    public KafkaAvroConsumer(String bootstrapServers, String schemaRegistryUrl, String topic, String groupId) {
        this.topic = topic;
        this.objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        System.out.println("\n=== Creating Kafka Consumer ===");
        System.out.println("Topic: " + topic);
        System.out.println("Bootstrap servers: " + bootstrapServers);
        System.out.println("Schema Registry URL: " + schemaRegistryUrl);
        System.out.println("Group ID: " + groupId);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
        props.put(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");

        // ДЛЯ ОТЛАДКИ
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "45000");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "10");

        System.out.println("Consumer properties configured");

        try {
            this.consumer = new KafkaConsumer<>(props);
            this.consumer.subscribe(Collections.singletonList(topic));
            System.out.println("✅ Consumer subscribed to topic: " + topic);
            System.out.println("=".repeat(80));
        } catch (Exception e) {
            System.err.println("❌ Error creating consumer: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public void consumeMessages(int maxMessages) {
        int messageCount = 0;

        try {
            while (messageCount < maxMessages) {
                ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, GenericRecord> record : records) {
                    messageCount++;
                    printMessage(record, messageCount);

                    if (messageCount >= maxMessages) {
                        break;
                    }
                }
            }
        } finally {
            consumer.close();
            System.out.println("Consumer closed.");
        }
    }

    public void consumeContinuously() {
        System.out.println("Starting continuous consumption. Press Ctrl+C to stop.");
        int messageCount = 0;

        try {
            while (true) {
                ConsumerRecords<String, GenericRecord> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, GenericRecord> record : records) {
                    messageCount++;
                    printMessage(record, messageCount);
                }
            }
        } catch (Exception e) {
            System.err.println("Error during consumption: " + e.getMessage());
        } finally {
            consumer.close();
        }
    }

    private void printMessage(ConsumerRecord<String, GenericRecord> record, int messageNumber) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MESSAGE #" + messageNumber);
        System.out.println("=".repeat(80));

        System.out.println("Topic: " + record.topic());
        System.out.println("Partition: " + record.partition());
        System.out.println("Offset: " + record.offset());
        System.out.println("Timestamp: " + record.timestamp());
        System.out.println("Key: " + record.key());

        System.out.println("\nValue (Avro GenericRecord):");
        System.out.println("-".repeat(40));

        try {
            // Преобразуем GenericRecord в JSON для красивого вывода
            String json = objectMapper.writeValueAsString(record.value());
            System.out.println(json);
        } catch (Exception e) {
            // Если не получается в JSON, выводим как есть
            System.out.println(record.value().toString());
        }

        // Дополнительно выводим отдельные поля
        System.out.println("\nIndividual Fields:");
        System.out.println("-".repeat(40));
        GenericRecord value = record.value();
        value.getSchema().getFields().forEach(field -> {
            Object fieldValue = value.get(field.name());
            System.out.println(field.name() + ": " + fieldValue +
                    " (type: " + field.schema().getType() + ")");
        });

        System.out.println("\nSchema Info:");
        System.out.println("-".repeat(40));
        System.out.println("Schema Name: " + value.getSchema().getName());
        System.out.println("Schema Namespace: " + value.getSchema().getNamespace());
        System.out.println("Schema Full Name: " + value.getSchema().getFullName());
    }

    public static void main(String[] args) {
        // Конфигурация по умолчанию
        String bootstrapServers = "localhost:9092";
        String schemaRegistryUrl = "http://localhost:8081";
        String topic = "customers-db.public.users";  // или "customers-db.public.orders"
        String groupId = "debezium-consumer-group";
        int maxMessages = 10;

        // Можно переопределить через аргументы командной строки
        if (args.length >= 4) {
            bootstrapServers = args[0];
            schemaRegistryUrl = args[1];
            topic = args[2];
            groupId = args[3];
            if (args.length >= 5) {
                maxMessages = Integer.parseInt(args[4]);
            }
        }

        KafkaAvroConsumer consumer = new KafkaAvroConsumer(
                bootstrapServers, schemaRegistryUrl, topic, groupId
        );

        if (maxMessages > 0) {
            consumer.consumeMessages(maxMessages);
        } else {
            consumer.consumeContinuously();
        }
    }
}