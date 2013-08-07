package org.projectfloodlight.sync.internal.config;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The authentication scheme to use for authenticating RPC connections
 * @author readams
 */
public enum AuthScheme {
    /**
     * The channel will be unauthenticated
     */
    NO_AUTH,
    /**
     * Use a shared secret with an HMAC-SHA1 challenge/response to 
     * authenticate
     */
    CHALLENGE_RESPONSE;
    
    @JsonCreator
    public static AuthScheme forValue(String v) { 
        return AuthScheme.valueOf(v.toUpperCase().replace("-", "_"));
    }
}
