package com.qns.data.remote;

import com.qns.data.remote.model.*;
import java.util.*;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.*;

public interface ApiService {
    // Auth
    @POST("api/auth/register") Single<AuthResponse> register(@Body AuthRequest r);
    @POST("api/auth/login")    Single<AuthResponse> login(@Body AuthRequest r);
    @POST("api/auth/refresh")  Single<AuthResponse> refresh(@Body Map<String,String> b);
    @DELETE("api/auth/logout") Single<Map<String,String>> logout(@Body Map<String,String> b);
    // Keys
    @GET("api/keys/bundle/{uid}")  Single<Map<String,Object>> getKeyBundle(@Path("uid") String uid);
    @POST("api/keys/prekeys")      Single<Map<String,Object>> uploadPrekeys(@Body Map<String,Object> b);
    // Users
    @GET("api/users/search")       Single<List<Map<String,Object>>> searchUsers(@Query("q") String q);
    @GET("api/users/{uid}")        Single<Map<String,Object>> getUser(@Path("uid") String uid);
    // Chats
    @GET("api/chats")              Single<List<Map<String,Object>>> getChats();
    @POST("api/chats")             Single<Map<String,Object>> createChat(@Body Map<String,Object> b);
    @GET("api/chats/{cid}/messages") Single<List<MessageResponse>> getMessages(
        @Path("cid") String cid, @Query("before") Long before, @Query("limit") int limit);
    // Admin
    @GET("api/admin/stats")                          Single<Map<String,Object>> getAdminStats();
    @GET("api/admin/users")                          Single<Map<String,Object>> getAdminUsers(@Query("q") String q, @Query("page") int page);
    @GET("api/admin/users/{uid}")                    Single<Map<String,Object>> getAdminUser(@Path("uid") String uid);
    @PUT("api/admin/users/{uid}")                    Single<Map<String,String>> updateAdminUser(@Path("uid") String uid, @Body Map<String,Object> b);
    @DELETE("api/admin/users/{uid}")                 Single<Map<String,String>> deleteAdminUser(@Path("uid") String uid);
    @POST("api/admin/users/{uid}/scam")              Single<Map<String,String>> setScam(@Path("uid") String uid, @Body Map<String,Object> b);
    @DELETE("api/admin/messages/{mid}")              Single<Map<String,String>> deleteMessage(@Path("mid") String mid);
    @DELETE("api/admin/chats/{cid}/messages")        Single<Map<String,String>> deleteAllMessages(@Path("cid") String cid);
    @DELETE("api/admin/users/{uid}/sessions")        Single<Map<String,String>> revokeSessions(@Path("uid") String uid);
}
