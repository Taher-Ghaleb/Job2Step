package com.job2step;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(new UserRepository());
    }

    @Test
    void registersValidUser() {
        User user = userService.register(1, "Grace Hopper", "grace@example.com");

        assertEquals(1, user.getId());
        assertEquals("Grace Hopper", user.getName());
        assertEquals("grace@example.com", user.getEmail());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.register(1, "  ", "grace@example.com"));
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.register(1, "Grace Hopper", "invalid-email"));
    }

    @Test
    void removesExistingUser() {
        userService.register(1, "Grace Hopper", "grace@example.com");

        assertTrue(userService.removeUser(1));
    }
}
