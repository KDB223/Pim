package com.kdb.pim.network;

import com.kdb.pim.data.youtuberesponse.YoutubeSnippet;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * Retrofit service interface to fetch {@link YoutubeSnippet} for video ID
 */
public interface YoutubeSnippetService {
    @Headers({"X-Android-Package: com.kdb.pim"})
    @GET("?part=snippet&fields=items(id,snippet(categoryId))")
    Call<YoutubeSnippet> getYoutubeSnippetResponse(
            @Query("key") String apiKey,
            @Query(value = "id", encoded = true) String commaSeparatedIds,
            @Header("X-Android-Cert") String cert
    );
}
