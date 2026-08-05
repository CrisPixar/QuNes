package com.qns.di;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.WebSocketClient;
import com.qns.BuildConfig;
import com.qns.data.remote.ServerRepository;
import com.qns.data.remote.TokenStore;
import com.qns.utils.Constants;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import dagger.Module; import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
@Module @InstallIn(SingletonComponent.class)
public class NetworkModule {
    @Provides @Singleton
    OkHttpClient provideOkHttp(TokenStore tokens) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                okhttp3.Request request = chain.request();
                String token = tokens.get();
                if (token.isEmpty() || request.header("Authorization") != null) return chain.proceed(request);
                return chain.proceed(request.newBuilder().header("Authorization", "Bearer " + token).build());
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
    @Provides @Singleton
    ApiService provideApiService(OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(Constants.RETROFIT_BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build().create(ApiService.class);
    }
    @Provides @Singleton
    WebSocketClient provideWsClient(OkHttpClient client, ServerRepository servers) { return new WebSocketClient(client, servers); }
}
