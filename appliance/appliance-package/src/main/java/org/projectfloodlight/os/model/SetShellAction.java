package org.projectfloodlight.os.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Action model representing setting the shell of a system user
 * @author readams
 */
public class SetShellAction implements OSModel {
    public enum Shell {
        FIRSTBOOT,
        CLI;
        
        @JsonCreator
        public static Shell forValue(String v) { 
            return Shell.valueOf(v.toUpperCase().replace("-", "_"));
        }
    }

    private Shell shell;
    private String user;
    
    public Shell getShell() {
        return shell;
    }
    public void setShell(Shell shell) {
        this.shell = shell;
    }
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
}
