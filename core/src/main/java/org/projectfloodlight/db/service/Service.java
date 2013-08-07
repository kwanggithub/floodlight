package org.projectfloodlight.db.service;

import org.projectfloodlight.db.TreespaceNotFoundException;
import org.projectfloodlight.db.auth.AuthService;

public interface Service {
    public AuthService getAuthService();
    public Treespace getTreespace(String name) throws TreespaceNotFoundException;
    void setAuthService(AuthService authService);
}
