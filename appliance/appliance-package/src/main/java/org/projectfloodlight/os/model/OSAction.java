package org.projectfloodlight.os.model;

import org.projectfloodlight.os.IOSActionlet.ActionType;

/**
 * Model class to represent operating system actions to be performed in 
 * the privileged wrapper
 * @author readams
 */
public class OSAction implements OSModel {
    private PowerAction powerAction;
    private TimeAction timeAction;
    private RegenKeysAction regenKeysAction;
    private SetShellAction setShellAction;
    private SetPasswordAction setPasswordAction;

    @ActionApplyType({ActionType.POWER})
    public PowerAction getPowerAction() {
        return powerAction;
    }
    public void setPowerAction(PowerAction powerAction) {
        this.powerAction = powerAction;
    }
    
    public TimeAction getTimeAction() {
        return timeAction;
    }
    public void setTimeAction(TimeAction timeAction) {
        this.timeAction = timeAction;
    }
    
    @ActionApplyType({ActionType.REGENERATE_KEYS})
    public RegenKeysAction getRegenKeysAction() {
        return regenKeysAction;
    }
    public void setRegenKeysAction(RegenKeysAction regenKeysAction) {
        this.regenKeysAction = regenKeysAction;
    }

    @ActionApplyType({ActionType.SET_SHELL})
    public SetShellAction getSetShellAction() {
        return setShellAction;
    }
    public void setSetShellAction(SetShellAction setShellAction) {
        this.setShellAction = setShellAction;
    }
    
    @ActionApplyType({ActionType.SET_PASSWORD})
    public SetPasswordAction getSetPasswordAction() {
        return setPasswordAction;
    }
    public void setSetPasswordAction(SetPasswordAction setPasswordAction) {
        this.setPasswordAction = setPasswordAction;
    }
}
