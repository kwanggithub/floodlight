package org.projectfloodlight.db.rest.auth;

import com.google.common.base.Strings;
import org.restlet.data.Status;

/**
 * Plain Old Java Bean that gets auto-filled by restlet with the user input
 * during login (from the JSON/XML in the push body).
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class LoginRequest {
    private String user;
    private String password;
    private String service;
    private String host;
    private String port;

    public LoginRequest() {
    }

    public LoginRequest(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public void validate() throws BadLoginException {
        if (Strings.isNullOrEmpty(user)) {
            throw new BadLoginException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "No user set on session login request");
        }
        if (password == null) {
            throw new BadLoginException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "No password set on session login request");
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }
}
