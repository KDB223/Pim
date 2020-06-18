package com.kdb.pim.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Patterns;

import com.kdb.pim.BuildConfig;
import com.kdb.pim.data.Message;
import com.kdb.pim.data.youtuberesponse.Item;
import com.kdb.pim.data.youtuberesponse.YoutubeSnippet;
import com.kdb.pim.network.ServiceGenerator;
import com.kdb.pim.network.YoutubeSnippetService;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Utility class to check if message content has YouTube links in message content belongs to category
 * 27 (Education) or 28 (Sci. & Tech.)
 */
public class MessageUtils {
    private static final String TAG = "pim.msgutils";
    private static YoutubeSnippetService service;

    public interface ResultListener {
        void onResult(boolean distracting);
    }

    /**
     * Checks if message has YouTube links, fetches their corresponding category IDs using
     * {@link YoutubeSnippetService} and invokes the supplied callback
     * @param message The message whose content is to be checked
     * @param listener The callback invoked after category IDs are found
     * @param context {@link Context} required for getting application SHA-1 signature
     */
    public static void checkMessageForContent(Message message, ResultListener listener, Context context) {
        List<String> youtubeIds = extractYtVideoIds(message.getContent());
        AppExecutors.getInstance().networkIO().execute(() -> {
            if (!youtubeIds.isEmpty()) {
                if (service == null) {
                    service = ServiceGenerator.getService(YoutubeSnippetService.class);
                }
                StringBuilder idString = new StringBuilder();
                for (String id : youtubeIds) {
                    idString.append(id).append(",");
                }
                String ids = idString.substring(0, idString.length() - 1);

                // Call YouTube Data API
                callApi(ids, new Callback<YoutubeSnippet>() {
                    @Override
                    public void onResponse(Call<YoutubeSnippet> call, Response<YoutubeSnippet> response) {
                        Log.d(TAG, "onResponse: Got response");
                        YoutubeSnippet youtubeSnippet = response.body();
                        if (youtubeSnippet == null)
                            return;
                        for (Item item : youtubeSnippet.getItems()) {
                            Log.d(TAG, "onResponse: category Id for " + item.getId() + " = " + item.getSnippet().getCategoryId());
                            listener.onResult(
                                    // non-education and s&t
                                    !item.getSnippet().getCategoryId().equals("27")
                                            && !item.getSnippet().getCategoryId().equals("28")
                            );
                        }
                    }

                    @Override
                    public void onFailure(Call<YoutubeSnippet> call, Throwable t) {
                        Log.d(TAG, "onFailure: " + t.getLocalizedMessage());
                    }
                }, context);
            }
            else {
                listener.onResult(false);
            }
        });
    }

    /**
     * Calls YouTube Data API v3 using {@link YoutubeSnippetService}
     * @param ids The video IDs to check
     * @param callback Retrofit {@link Callback} to enqueue
     * @param context {@link Context} required for getting application SHA-1 signature
     */
    private static void callApi(String ids, Callback<YoutubeSnippet> callback, Context context) {
        Call<YoutubeSnippet> call = service.getYoutubeSnippetResponse(BuildConfig.API_KEY, ids, SignUtils.getSignature(context));
        call.enqueue(callback);
    }

    /**
     * Looks for YouTube links in the message content and returns their video IDs
     * @param content {@link Context} required for pattern matcher
     * @return {@link List} of YouTube video IDs in {@param content}
     */
    private static List<String> extractYtVideoIds(String content) {
        List<String> ids = new ArrayList<>();
        Matcher matcher = Patterns.WEB_URL.matcher(content);
        while (matcher.find()) {
            String link = formatLink(matcher.group());
            Log.d(TAG, "extractYtLinks: Found link - " + link);
            String ytVideoId = extractYtVideoId(link);
            if (ytVideoId != null) {
                Log.d(TAG, "extractYtLinks: Youtube link, video id = " + ytVideoId);
                ids.add(ytVideoId);
            }
        }
        return ids;
    }

    /**
     * Utility method to make sure links begin with "https://"
     * @param link The YouTube link
     * @return {@param link} beginning with "https://"
     */
    private static String formatLink(String link) {
        if (!link.startsWith("https://") && !link.startsWith("http://") && !link.contains("://")) {
            link = "https://" + link;
        }
        return link;
    }

    /**
     * Parses a YouTube video URL and extracts video IDs from it
     * @param link The YouTube video link
     * @return Video ID of the YouTube video specified by the link
     */
    private static String extractYtVideoId(String link) {
        try {
            URI uri = new URI(link);
            String host = uri.getHost().toLowerCase();
            String path = uri.getPath();
            String query = uri.getQuery();
            if (host.endsWith("youtube.com") && path.equals("/watch")) {
                Map<String, String> params = splitParams(query);
                return params.get("v");
            } else if (host.endsWith("youtu.be")) {
                return path.substring(1);
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Converts GET parameters to a {@link Map} object
     * @param query GET parameters string
     * @return {@link Map} containing all GET parameters and values
     */
    private static Map<String, String> splitParams(String query) {
        String[] params = query.split("&");
        Map<String, String> map = new HashMap<>();
        for (String param : params) {
            String key = param.split("=")[0];
            String value = param.split("=")[1];
            map.put(key, value);
        }
        return map;
    }
}
