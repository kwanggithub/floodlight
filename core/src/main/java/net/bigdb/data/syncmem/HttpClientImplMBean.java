package net.bigdb.data.syncmem;

import java.net.URI;

import net.bigdb.data.syncmem.HttpClientImpl.State;

public interface HttpClientImplMBean {

    public abstract State getState();

    public abstract URI getUri();

    public abstract HttpClientStats getStats();

}
