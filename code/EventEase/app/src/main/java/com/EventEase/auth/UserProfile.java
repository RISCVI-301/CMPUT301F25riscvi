package com.EventEase.auth;


public class UserProfile {
    public String uid;
    public String email;
    public String name;
    public long createdAt;


    public UserProfile() { /* Firestore needs empty ctor */ }


    public UserProfile(String uid, String email, String name, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.createdAt = createdAt;
    }
}