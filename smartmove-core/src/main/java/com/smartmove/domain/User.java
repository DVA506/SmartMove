package com.smartmove.domain;

import java.util.UUID;

public class User {

    private String id;
    private String name;
    private String email;
    private String city;

    public User() {
        // Required for JSON deserialization
    }

    public User(String name, String email, String city) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.email = email;
        this.city = city;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
