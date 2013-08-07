package org.projectfloodlight.sync.internal.store;

import org.projectfloodlight.sync.Versioned;
import org.projectfloodlight.sync.internal.version.VectorClock;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public final class VCVersioned<T> extends Versioned<T> {

    private static final long serialVersionUID = 8038484251323965062L;

    public VCVersioned(T object) {
        super(object);
    }

    @JsonCreator
    public VCVersioned(@JsonProperty("object") T object,
                       @JsonProperty("version") VectorClock version) {
        super(object, version);
    }
}
