package com.kdb.pim.utils;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class to get human-readable timestamps
 */
public class TimeUtils {
    private static final int SECOND_MILLIS = 1000;
    private static final int MINUTE_MILLIS = 60 * SECOND_MILLIS;
    private static final int HOUR_MILLIS = 60 * MINUTE_MILLIS;
    private static final int DAY_MILLIS = 24 * HOUR_MILLIS;

    /**
     * Converts a timestamp to human-readable format
     * @param timestamp The timestamp to convert
     * @return Human-readable timestamp in HH:MM AM/PM format (e.g. 03:10 AM)
     */
    public static String getReadableTime(Date timestamp) {
        DateFormat format = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return format.format(timestamp);
    }

    /**
     * Calculates time difference between a timestamp and current time, and returns a human-readable
     * string representing how long ago it was
     * @param time The timestamp to convert
     * @return Human-readable time difference string
     */
    public static String getTimeAgo(long time) {
        if (time < 1000000000000L) {
            // if timestamp given in seconds, convert to millis
            time *= 1000;
        }

        long now = System.currentTimeMillis();
        if (time > now || time <= 0) {
            return null;
        }

        final long diff = now - time;
        if (diff < MINUTE_MILLIS) {
            return "just now";
        } else if (diff < 2 * MINUTE_MILLIS) {
            return "a minute ago";
        } else if (diff < 50 * MINUTE_MILLIS) {
            return diff / MINUTE_MILLIS + " minutes ago";
        } else if (diff < 90 * MINUTE_MILLIS) {
            return "an hour ago";
        }  else if (diff < 24 * HOUR_MILLIS) {
            return "at " + getReadableTime(new Date(time));
        } else if (diff < 48 * HOUR_MILLIS) {
            return "yesterday";
        } else {
            return diff / DAY_MILLIS + " days ago";
        }
    }
}
