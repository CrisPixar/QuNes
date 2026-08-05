package com.qns.data.remote.model;

import java.util.Map;

public class AuthRequest {
    public String username;
    public String password;
    public Map<String, Object> publicKeys;

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public AuthRequest(String username, String password, Map<String, Object> publicKeys) {
        this(username, password);
        this.publicKeys = publicKeys;
    }
}
