package org.projectfloodlight.db.data.syncmem;

public interface SyncContent {
    public String getContentType();
    public byte[] getUpdate(SyncContent currentContent);
}