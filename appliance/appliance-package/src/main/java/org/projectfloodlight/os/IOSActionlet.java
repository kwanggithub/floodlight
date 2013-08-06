package org.projectfloodlight.os;

import java.io.File;

import org.projectfloodlight.os.model.OSAction;

/**
 * An interface for an actionlet, which is a way to perform some specific
 * non-configuration system action, such as a reboot.
 * @author readams
 */
public interface IOSActionlet extends IOSlet<IOSActionlet.ActionType> {
    public enum ActionType {
        POWER,
        NTP_DATE,
        SET_TIME,
        REGENERATE_KEYS,
        SET_SHELL,
        SET_PASSWORD
    }

    /**
     * Apply the action to the system
     * @param action The action to apply
     * @return a {@link WrapperOutput} containing the results of the operation
     */
    public WrapperOutput applyAction(File basePath, OSAction action);
}
