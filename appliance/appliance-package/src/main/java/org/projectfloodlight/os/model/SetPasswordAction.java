package org.projectfloodlight.os.model;

/**
 * Action model representing setting the password of a system user
 * @author readams
 */
public class SetPasswordAction implements OSModel {
    private String user;
    private String password;

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
}
