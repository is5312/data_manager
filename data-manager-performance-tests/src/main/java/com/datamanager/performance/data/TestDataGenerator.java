package com.datamanager.performance.data;

import net.datafaker.Faker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe test data generator using Faker library
 * Generates realistic test data for performance testing
 */
public class TestDataGenerator {

    private static final ThreadLocal<Faker> FAKER = ThreadLocal.withInitial(Faker::new);
    private static final AtomicLong COUNTER = new AtomicLong(0);

    /**
     * Generate a complete row of test data
     * @param emailDomain domain to use for email addresses
     * @param minAge minimum age
     * @param maxAge maximum age
     * @return map of column names to values
     */
    public Map<String, String> generateRowData(String emailDomain, int minAge, int maxAge) {
        Faker faker = FAKER.get();
        long counter = COUNTER.incrementAndGet();
        
        Map<String, String> data = new HashMap<>();
        
        // Generate realistic name
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String fullName = firstName + " " + lastName;
        data.put("name", fullName);
        
        // Generate unique email using counter
        String email = generateEmail(firstName, lastName, counter, emailDomain);
        data.put("email", email);
        
        // Generate random age
        int age = ThreadLocalRandom.current().nextInt(minAge, maxAge + 1);
        data.put("age", String.valueOf(age));
        
        return data;
    }

    /**
     * Generate a complete row of test data with default settings
     * @return map of column names to values
     */
    public Map<String, String> generateRowData() {
        return generateRowData("perftest.example.com", 18, 80);
    }

    /**
     * Generate a unique email address
     */
    private String generateEmail(String firstName, String lastName, long counter, String domain) {
        String localPart = firstName.toLowerCase() + "." + lastName.toLowerCase() + "." + counter;
        // Remove any special characters
        localPart = localPart.replaceAll("[^a-z0-9.]", "");
        return localPart + "@" + domain;
    }

    /**
     * Generate just a name
     */
    public String generateName() {
        Faker faker = FAKER.get();
        return faker.name().fullName();
    }

    /**
     * Generate just an email
     */
    public String generateEmail() {
        Faker faker = FAKER.get();
        long counter = COUNTER.incrementAndGet();
        String firstName = faker.name().firstName().toLowerCase();
        String lastName = faker.name().lastName().toLowerCase();
        return firstName + "." + lastName + "." + counter + "@perftest.example.com";
    }

    /**
     * Generate just an age
     */
    public int generateAge(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    /**
     * Generate batch of test data rows
     * @param count number of rows to generate
     * @param emailDomain domain for emails
     * @param minAge minimum age
     * @param maxAge maximum age
     * @return array of data maps
     */
    public Map<String, String>[] generateBatch(int count, String emailDomain, int minAge, int maxAge) {
        @SuppressWarnings("unchecked")
        Map<String, String>[] batch = new Map[count];
        
        for (int i = 0; i < count; i++) {
            batch[i] = generateRowData(emailDomain, minAge, maxAge);
        }
        
        return batch;
    }

    /**
     * Generate batch with default settings
     */
    public Map<String, String>[] generateBatch(int count) {
        return generateBatch(count, "perftest.example.com", 18, 80);
    }

    /**
     * Reset counter (useful for tests)
     */
    public void resetCounter() {
        COUNTER.set(0);
    }

    /**
     * Get current counter value (useful for debugging)
     */
    public long getCounterValue() {
        return COUNTER.get();
    }
}
