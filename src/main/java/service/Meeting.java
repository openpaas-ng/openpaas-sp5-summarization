package service;

import structures.Transcript;

public class Meeting {

    private final long id;
    private final Transcript content;

    public Meeting(long id, Transcript content) {
        this.id = id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public Transcript getContent() {
        return content;
    }
}