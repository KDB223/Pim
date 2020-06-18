package com.kdb.pim.data;

public class Message {
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_TEXT = "text";

    private String from, to;
    private String content;
    private Object serverTimestamp;
    private boolean distracting;
    private boolean seen;
    private String type;
    private String imageUrl;
    private String thumbUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isDistracting() {
        return distracting;
    }

    public void setDistracting(boolean distracting) {
        this.distracting = distracting;
    }

    public Message() { // For Firebase
    }

    public Message(String from, String to, String content, Object timestamp) {
        this.from = from;
        this.to = to;
        this.content = content;
        this.serverTimestamp = timestamp;
    }

    public boolean isSeen() {
        return seen;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Object getServerTimestamp() {
        return serverTimestamp;
    }

    public void setServerTimestamp(Object serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }
}
