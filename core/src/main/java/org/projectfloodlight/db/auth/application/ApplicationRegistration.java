package org.projectfloodlight.db.auth.application;

import java.nio.charset.StandardCharsets;

import org.projectfloodlight.db.auth.session.RandomCookieGenerator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.io.BaseEncoding;

public class ApplicationRegistration {
    private final String name;
    private final String secret;

    @JsonCreator
    public ApplicationRegistration(@JsonProperty("name") String name,
                                   @JsonProperty("secret") String secret) {
        this.name = name;
        this.secret = secret;
    }
    public ApplicationRegistration(String name) {
        this(name, new RandomCookieGenerator(8).createCookie());
    }
    public String getName() {
        return name;
    }
    public String getSecret() {
        return secret;
    }
    @JsonIgnore
    public String getBasicAuth() {
        BaseEncoding enc = BaseEncoding.base64();
        String decoded = getName() + ":" + getSecret();
        String auth = enc.encode(decoded.getBytes(StandardCharsets.UTF_8));
        return "Basic " + auth;
    }
}