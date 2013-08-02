package org.sdnplatform.os.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Action model representing power state transitions
 * @author readams
 */
public class PowerAction implements OSModel {
    public enum Action {
        REBOOT,
        SHUTDOWN;
        
        @JsonCreator
        public static Action forValue(String v) { 
            return Action.valueOf(v.toUpperCase().replace("-", "_"));
        }
    }
   
    private Action action;

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }
}
