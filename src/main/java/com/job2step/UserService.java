package com.job2step;

public class UserService {

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    public User register(long id, String name, String email) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (repository.findById(id).isPresent()) {
            throw new IllegalStateException("User already exists with id: " + id);
        }

        return repository.save(new User(id, name.trim(), email.trim().toLowerCase()));
    }

    public User getUser(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public boolean removeUser(long id) {
        return repository.deleteById(id);
    }
}
