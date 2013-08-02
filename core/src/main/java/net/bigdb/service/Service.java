package net.bigdb.service;

import net.bigdb.TreespaceNotFoundException;
import net.bigdb.auth.AuthService;

public interface Service {
    public AuthService getAuthService();
    public Treespace getTreespace(String name) throws TreespaceNotFoundException;
    void setAuthService(AuthService authService);
}
