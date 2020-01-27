package com.mongodb.mongomirror;

public class MongoMirrorStatus {

    protected String stage;
    protected String phase;
    protected String errorMessage;

    public MongoMirrorStatus() {
        super();
    }
    
    public boolean isInitialSync() {
        return this instanceof MongoMirrorStatusInitialSync;
    }
    
    public boolean isOplogSync() {
        return this instanceof MongoMirrorStatusOplogSync;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

}