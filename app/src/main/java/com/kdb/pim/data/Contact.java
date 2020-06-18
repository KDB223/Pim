package com.kdb.pim.data;

import com.google.firebase.database.Exclude;

import java.util.HashMap;
import java.util.Map;

public class Contact {
    public String name;
    public String phone;
    public String status;
    public String thumb;

    @Exclude
    public String uid;

    public Contact() {
    }

    public Contact(String name, String status, String phone, String thumb) {
        this.name = name;
        this.status = status;
        this.phone = phone;
        this.thumb = thumb;
    }

    public Contact(Map<String, String> contactMap) {
        this.name = contactMap.get("contactName");
        this.status = contactMap.get("contactStatus");
        this.phone = contactMap.get("contactPhone");
        this.thumb = contactMap.get("contactThumb");
        this.uid = contactMap.get("contactUid");
    }

    public HashMap<String, String> toMap() {
        HashMap<String, String> map = new HashMap<>();
        map.put("contactName", name);
        map.put("contactStatus", status);
        map.put("contactPhone", phone);
        map.put("contactThumb", thumb);
        map.put("contactUid", uid);
        return map;
    }

    public void createFromMap(HashMap<String, String> map) {
        name = map.get("contactName");
        status = map.get("contactStatus");
        phone = map.get("contactPhone");
        thumb = map.get("contactThumb");
        uid = map.get("contactUid");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getThumb() {
        return thumb;
    }

    public void setThumb(String thumb) {
        this.thumb = thumb;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
