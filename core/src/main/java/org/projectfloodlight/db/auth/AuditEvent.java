package org.projectfloodlight.db.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.collect.ImmutableMap;


public class AuditEvent {
    private final long timeStamp;
    private final String type;
    private final ImmutableMap<String, String> avPairs;

    public static class Builder {
        private String type;
        private Map<String, String> avPairs;

        public Builder type(String eventType) {
            type = eventType;
            return this;
        }
        public Builder avPair(String attr, String val) {
            if (avPairs == null) {
                avPairs = new LinkedHashMap<String, String>();
            }
            avPairs.put(attr, val);
            return this;
        }
        public AuditEvent build() {
            return new AuditEvent(this);
        }
    }
    private AuditEvent(Builder build) {
        timeStamp = System.currentTimeMillis();
        this.type = build.type;
        avPairs = ImmutableMap.copyOf(build.avPairs);
    }

    public long getTimeStamp() {
        return timeStamp;
    }
    public String getType() {
        return type;
    }
    public ImmutableMap<String, String> getAvPairs() {
        return avPairs;
    }
    public void commit() {
        AuditServer.getInstance().commit(this);
    }
}
