package com.js.salesman.models;

public class LoginResponse {
    public int code;
    public boolean success;
    public String message;
    public Data data;

    public static class Data {
        public String token;
        public User user;
    }

    public static class User {
        public int id;
        public String username;
        public String role;
        public String full_name;
    }
}
