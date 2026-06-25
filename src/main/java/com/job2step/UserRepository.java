package com.job2step;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserRepository {

    private final Map<Long, User> users = new HashMap<>();

    public User save(User user) {
        users.put(user.getId(), user);
        return user;
    }

    public Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    public boolean deleteById(long id) {
        return users.remove(id) != null;
    }

    public int count() {
        return users.size();
    }
}
