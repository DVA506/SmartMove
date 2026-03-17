package com.smartmove.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void constructorSetsFieldsAndGeneratesId() {
        User user = new User("John", "john@test.com", "London");

        assertNotNull(user.getId());
        assertEquals("John", user.getName());
        assertEquals("john@test.com", user.getEmail());
        assertEquals("London", user.getCity());
    }

    @Test
    void settersAndGettersWorkCorrectly() {
        User user = new User();

        user.setId("u1");
        user.setName("Alice");
        user.setEmail("alice@test.com");
        user.setCity("Rome");

        assertEquals("u1", user.getId());
        assertEquals("Alice", user.getName());
        assertEquals("alice@test.com", user.getEmail());
        assertEquals("Rome", user.getCity());
    }

    @Test
    void idCanBeOverwritten() {
        User user = new User("John", "john@test.com", "London");

        String originalId = user.getId();
        user.setId("custom-id");

        assertNotEquals(originalId, user.getId());
        assertEquals("custom-id", user.getId());
    }
}