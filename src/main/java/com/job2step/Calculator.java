package com.job2step;

public class Calculator {

    public int add(int left, int right) {
        return left + right;
    }

    public int subtract(int left, int right) {
        return left - right;
    }

    public int multiply(int left, int right) {
        return left * right;
    }

    public double divide(int left, int right) {
        if (right == 0) {
            throw new IllegalArgumentException("Division by zero is not allowed");
        }
        return (double) left / right;
    }
}
