package com.job2step;

public class App {

    public static void main(String[] args) {
        Calculator calculator = new Calculator();
        System.out.println("2 + 3 = " + calculator.add(2, 3));

        UserRepository repository = new UserRepository();
        UserService userService = new UserService(repository);
        User user = userService.register(1, "Ada Lovelace", "ada@example.com");
        System.out.println("Registered user: " + user.getName());
    }
}
