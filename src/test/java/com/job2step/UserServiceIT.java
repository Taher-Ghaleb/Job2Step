package com.job2step;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests exercise multiple components together (service + repository).
 */
class UserServiceIT {

    private UserRepository repository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        repository = new UserRepository();
        userService = new UserService(repository);
    }

    @Test
    void persistsAndRetrievesUserAcrossLayers() {
        userService.register(42, "Alan Turing", "alan@example.com");

        User stored = userService.getUser(42);

        assertEquals("Alan Turing", stored.getName());
        assertEquals("alan@example.com", stored.getEmail());
        assertEquals(1, repository.count());
    }

    @Test
    void preventsDuplicateRegistration() {
        userService.register(7, "Katherine Johnson", "katherine@example.com");

        assertThrows(IllegalStateException.class,
                () -> userService.register(7, "Duplicate", "dup@example.com"));
        assertEquals(1, repository.count());
    }

    @Test
    void removesUserFromRepository() {
        userService.register(9, "Linus Torvalds", "linus@example.com");

        assertTrue(userService.removeUser(9));
        assertEquals(0, repository.count());
        assertThrows(IllegalArgumentException.class, () -> userService.getUser(9));
    }
}
