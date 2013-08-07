package org.projectfloodlight.db.data.syncmem;

import java.net.URI;

import org.projectfloodlight.db.data.syncmem.HttpClientImpl.State;

public interface HttpClientImplMBean {

    public abstract State getState();

    public abstract URI getUri();

    public abstract HttpClientStats getStats();

}
