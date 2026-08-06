package com.qns.di;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.qns.BuildConfig;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.AuthEvents;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.TokenRefresher;
import com.qns.data.remote.TokenStore;
import com.qns.data.remote.WebSocketClient;
import com.qns.utils.Constants;

import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

@Module
@InstallIn(SingletonComponent.class)
public class NetworkModule {

    /** Отдельный HTTP-клиент для refresh — БЕЗ auth-interceptor и authenticator, чтобы не было рекурсии. */
    @Provides
    @Singleton
    @Named("refresh")
    OkHttpClient provideRefreshOkHttp() {
        return new OkHttpClient.Builder()
            .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();
    }

    @Provides
    @Singleton
    Gson provideGson() {
        JsonDeserializer<Boolean> booleanAdapter = (json, type, context) -> {
            if (json == null || json.isJsonNull()) return false;
            if (json.getAsJsonPrimitive().isBoolean()) return json.getAsBoolean();
            if (json.getAsJsonPrimitive().isNumber()) return json.getAsInt() != 0;
            return "true".equalsIgnoreCase(json.getAsString()) || "1".equals(json.getAsString());
        };
        return new GsonBuilder()
            .registerTypeAdapter(Boolean.class, booleanAdapter)
            .registerTypeAdapter(boolean.class, booleanAdapter)
            .create();
    }

    @Provides
    @Singleton
    @Named("refresh")
    ApiService provideRefreshApiService(@Named("refresh") OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
            .baseUrl(Constants.RETROFIT_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build()
            .create(ApiService.class);
    }

    @Provides
    @Singleton
    TokenRefresher provideTokenRefresher(
        @Named("refresh") ApiService refreshApi,
        ServerRepository servers,
        com.qns.data.local.PrefsStore prefs,
        TokenStore tokens,
        AuthEvents events
    ) {
        return new TokenRefresher(refreshApi, servers, prefs, tokens, events);
    }

    @Provides
    @Singleton
    OkHttpClient provideOkHttp(TokenStore tokens, TokenRefresher tokenRefresher) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                String token = tokens.get();
                if (token.isEmpty() || request.header("Authorization") != null) return chain.proceed(request);
                return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
            })
            .authenticator((route, response) -> {
                if (response.code() != 401) return null;
                Request request = response.request();
                if (isAuthEndpoint(request.url())) return null;
                if (!tokenRefresher.tryRefresh()) return null;
                String token = tokens.get();
                if (token == null || token.isEmpty()) return null;
                return request.newBuilder().header("Authorization", "Bearer " + token).build();
            })
            .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor log = new HttpLoggingInterceptor();
            log.setLevel(HttpLoggingInterceptor.Level.BASIC);
            builder.addInterceptor(log);
        }
        return builder.build();
    }

    @Provides
    @Singleton
    ApiService provideApiService(OkHttpClient client, Gson gson) {
        return new Retrofit.Builder()
            .baseUrl(Constants.RETROFIT_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build()
            .create(ApiService.class);
    }

    @Provides
    @Singleton
    WebSocketClient provideWsClient(OkHttpClient client, ServerRepository servers, com.qns.utils.NotificationHelper notifications, AuthEvents events) {
        return new WebSocketClient(client, servers, notifications, events);
    }

    private static boolean isAuthEndpoint(HttpUrl url) {
        String path = url.encodedPath();
        return path.contains("/auth/login")
            || path.contains("/auth/register")
            || path.contains("/auth/refresh");
    }
}
