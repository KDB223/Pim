package com.kdb.pim.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Utility class used to create an instance of {@link YoutubeSnippetService} for Retrofit
 */
public class ServiceGenerator {
    private static final String BASE_URL = "https://www.googleapis.com/youtube/v3/videos/";
    private static Gson gson = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    private static HttpLoggingInterceptor logger = new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC);

    private static OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
            .addInterceptor(logger);

    private static Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(httpClientBuilder.build())
            .build();

    public static <T> T getService(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }
}
