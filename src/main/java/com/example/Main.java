package com.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("=== Debezium Kafka Consumer Demo ===");

        // КОНФИГУРАЦИЯ ДЛЯ ЗАПУСКА ИЗ IDE
        String bootstrapServers = "10.127.1.2:9094,10.127.1.2:9095,10.127.1.2:9096";
        String schemaRegistryUrl = "http://10.127.1.2:8081";
        String groupId = "debezium-java-consumer-ide";

        System.out.println("Configuration:");
        System.out.println("Bootstrap servers: " + bootstrapServers);
        System.out.println("Schema Registry: " + schemaRegistryUrl);
        System.out.println("Group ID: " + groupId);
        System.out.println("=".repeat(80));

        // Запуск потребителя для users
        System.out.println("\n🎯 Starting consumer for USERS topic...");
        Thread usersThread = new Thread(() -> {
            KafkaAvroConsumer usersConsumer = new KafkaAvroConsumer(
                    bootstrapServers,
                    schemaRegistryUrl,
                    "customers-db.public.users",
                    groupId + "-users"
            );
            usersConsumer.consumeMessages(5);  // Получим 5 сообщений
        });

        // Запуск потребителя для orders
        System.out.println("\n🎯 Starting consumer for ORDERS topic...");
        Thread ordersThread = new Thread(() -> {
            KafkaAvroConsumer ordersConsumer = new KafkaAvroConsumer(
                    bootstrapServers,
                    schemaRegistryUrl,
                    "customers-db.public.orders",
                    groupId + "-orders"
            );
            ordersConsumer.consumeMessages(5);  // Получим 5 сообщений
        });

        // Запуск потоков
        usersThread.start();
        ordersThread.start();

        // Ожидание завершения
        try {
            usersThread.join();
            ordersThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n✅ All consumers finished!");
    }
}