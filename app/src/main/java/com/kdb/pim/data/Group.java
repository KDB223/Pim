package com.kdb.pim.data;

import java.util.Set;

public class Group {
    private String title;
    private String description;
    private Set<Contact> participants;
    private boolean work;

    public Group(String title, String description, Set<Contact> participants, boolean work) {
        this.title = title;
        this.description = description;
        this.participants = participants;
        this.work = work;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Contact> getParticipants() {
        return participants;
    }

    public void setParticipants(Set<Contact> participants) {
        this.participants = participants;
    }

    public boolean isWork() {
        return work;
    }

    public void setWork(boolean work) {
        this.work = work;
    }
}
