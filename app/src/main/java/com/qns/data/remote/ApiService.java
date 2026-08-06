package com.qns.data.remote;

import com.qns.data.remote.model.AuthRequest;
import com.qns.data.remote.model.AuthResponse;
import com.qns.data.remote.model.MessageResponse;

import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HTTP;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface ApiService {
    @POST
    Single<AuthResponse> register(@Url String url, @Body AuthRequest request);

    @POST
    Single<AuthResponse> login(@Url String url, @Body AuthRequest request);

    @POST
    Single<AuthResponse> refresh(@Url String url, @Body Map<String, String> body);

    @HTTP(method = "DELETE", hasBody = true)
    Single<Map<String, String>> logout(@Url String url, @Body Map<String, String> body);

    @GET
    Single<List<Map<String, Object>>> getSessions(@Url String url);

    @DELETE
    Single<Map<String, String>> revokeAllSessions(@Url String url);

    @DELETE
    Single<Map<String, String>> revokeSession(@Url String url);

    @GET
    Single<Map<String, Object>> getKeyBundle(@Url String url);

    @POST
    Single<Map<String, Object>> uploadPrekeys(@Url String url, @Body Map<String, Object> body);

    @PUT
    Single<Map<String, Object>> uploadIdentityKeys(@Url String url, @Body Map<String, Object> body);

    @GET
    Single<List<Map<String, Object>>> searchUsers(@Url String url, @Query("q") String query);

    @GET
    Single<Map<String, Object>> getUser(@Url String url);

    @GET
    Single<List<Map<String, Object>>> getChats(@Url String url);

    @POST
    Single<Map<String, Object>> createChat(@Url String url, @Body Map<String, Object> body);

    @GET
    Single<List<MessageResponse>> getMessages(
        @Url String url,
        @Query("before") Long before,
        @Query("limit") int limit
    );

    @GET
    Single<Map<String, Object>> getAdminStats(@Url String url);

    @GET
    Single<Map<String, Object>> getAdminUsers(@Url String url, @Query("q") String query, @Query("page") int page);

    @GET
    Single<Map<String, Object>> getAdminUser(@Url String url);

    @PUT
    Single<Map<String, String>> updateAdminUser(@Url String url, @Body Map<String, Object> body);

    @DELETE
    Single<Map<String, String>> deleteAdminUser(@Url String url);

    @POST
    Single<Map<String, String>> setScam(@Url String url, @Body Map<String, Object> body);

    @DELETE
    Single<Map<String, String>> deleteMessage(@Url String url);

    @DELETE
    Single<Map<String, String>> deleteAllMessages(@Url String url);

    @DELETE
    Single<Map<String, String>> revokeAdminSessions(@Url String url);
}
