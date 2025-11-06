package com.EventEase.auth;

public class DevAuthManager implements AuthManager {
    private final String uid;
    public DevAuthManager(String uid) { this.uid = uid; }
    @Override public String getUid() { return uid; }
}
