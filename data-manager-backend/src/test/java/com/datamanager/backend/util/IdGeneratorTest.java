package com.datamanager.backend.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void generatePhysicalTableName_GeneratesUniqueNames() {
        // When
        String name1 = IdGenerator.generatePhysicalTableName();
        String name2 = IdGenerator.generatePhysicalTableName();

        // Then
        assertThat(name1).isNotEqualTo(name2);
    }

    @Test
    void generatePhysicalTableName_FollowsNamingConvention() {
        // When
        String name = IdGenerator.generatePhysicalTableName();

        // Then
        assertThat(name).startsWith("tbl_");
        assertThat(name.length()).isGreaterThan(4);
    }

    @Test
    void generatePhysicalTableName_NoCollisions_InRapidSuccession() {
        // Given
        Set<String> names = new HashSet<>();
        int iterations = 100;

        // When
        for (int i = 0; i < iterations; i++) {
            names.add(IdGenerator.generatePhysicalTableName());
        }

        // Then
        assertThat(names).hasSize(iterations); // All unique
    }

    @Test
    void generateUlid_GeneratesUniqueIds() {
        // When
        String id1 = IdGenerator.generateUlid();
        String id2 = IdGenerator.generateUlid();

        // Then
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void generateUlid_ReturnsLowercase() {
        // When
        String id = IdGenerator.generateUlid();

        // Then
        assertThat(id).isLowerCase();
    }
}

