package com.qns.di;
import com.qns.data.remote.ApiService;
import com.qns.data.remote.WebSocketClient;
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
    OkHttpClient provideOkHttp() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BODY);
        return new OkHttpClient.Builder()
            .connectTimeout(Constants.HTTP_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.HTTP_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.HTTP_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .addInterceptor(log).build();
    }
    @Provides @Singleton
    ApiService provideApiService(OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(Constants.SERVER_BASE_URL).client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
            .build().create(ApiService.class);
    }
    @Provides @Singleton
    WebSocketClient provideWsClient(OkHttpClient client) { return new WebSocketClient(client); }
}
