package com.qns.data.remote.model;
import com.google.gson.annotations.SerializedName;
public class AuthResponse {
    @SerializedName("accessToken")  public String accessToken;
    @SerializedName("refreshToken") public String refreshToken;
    @SerializedName("user")         public UserInfo user;
    public static class UserInfo {
        public String id, username, role;
        public boolean isScam;
    }
}
