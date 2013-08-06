package org.projectfloodlight.os.model;

import com.fasterxml.jackson.annotation.JsonCreator;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Action model representing regeneration of cryptographic keys for the system
 * @author readams
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class RegenKeysAction implements OSModel {

    public enum Action {
        WEB_SSL,
        SSH;
        
        @JsonCreator
        public static Action forValue(String v) { 
            return Action.valueOf(v.toUpperCase().replace("-", "_"));
        }
    }
    
    private Action[] actions;

    public Action[] getActions() {
        return actions;
    }

    public void setActions(Action[] actions) {
        this.actions = actions;
    }
}
