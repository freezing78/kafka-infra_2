// TestSimple.java - создайте для проверки
package com.example;

public class TestSimple {
    public static void main(String[] args) {
        System.out.println("Test: Hello from Kafka Consumer!");

        // Просто проверяем создание объекта
        try {
            KafkaAvroConsumer consumer = new KafkaAvroConsumer(
                    "10.127.1.2:9094",
                    "http://10.127.1.2:8081",
                    "customers-db.public.users",
                    "test-group"
            );
            System.out.println("✅ Consumer created successfully!");
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }
}