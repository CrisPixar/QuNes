package com.qns.data.remote.model;

public final class SessionInfo {
    public final String id;
    public final String ip;
    public final String userAgent;
    public final long createdAt;
    public final long expiresAt;

    public SessionInfo(String id, String ip, String userAgent, long createdAt, long expiresAt) {
        this.id = id;
        this.ip = ip;
        this.userAgent = userAgent;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
