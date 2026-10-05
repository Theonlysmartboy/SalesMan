package com.js.salesman.models;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey
    @NonNull
    private String id;

    @ColumnInfo(name = "userName")
    private String userName;

    @ColumnInfo(name = "hasPin")
    private boolean hasPin;

    @ColumnInfo(name = "pinHash")
    private String pinHash;

    private String role;

    @ColumnInfo(name = "fullName")
    private String fullName;

    private String token;

    public UserEntity(@NonNull String id, String userName, boolean hasPin, String pinHash,
                      String role, String fullName, String token) {
        this.id = id;
        this.userName = userName;
        this.hasPin = hasPin;
        this.pinHash = pinHash;
        this.role = role;
        this.fullName = fullName;
        this.token = token;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isHasPin() {
        return hasPin;
    }

    public void setHasPin(boolean hasPin) {
        this.hasPin = hasPin;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
