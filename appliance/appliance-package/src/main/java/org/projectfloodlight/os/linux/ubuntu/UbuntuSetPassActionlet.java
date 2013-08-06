package org.projectfloodlight.os.linux.ubuntu;

import static org.projectfloodlight.os.ConfigletUtil.runWithPipe;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSActionlet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.WrapperOutput.Status;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.SetPasswordAction;

public class UbuntuSetPassActionlet implements IOSActionlet {
    private static final String CHPASSWD = "/usr/sbin/chpasswd";

    @Override
    public EnumSet<ActionType> provides() {
        return EnumSet.of(ActionType.SET_PASSWORD);
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput wo = new WrapperOutput();
        SetPasswordAction spa = action.getSetPasswordAction();
        if (spa.getUser() == null || spa.getUser().length() == 0) {
            wo.add(WrapperOutput.error(Status.ARG_ERROR, "Username not specified"));
        }
        if (spa.getPassword() == null) {
            wo.add(WrapperOutput.error(Status.ARG_ERROR, "Password not specified"));
        }
        if (!wo.succeeded()) return wo;

        StringBuilder input = new StringBuilder();
        input.append(spa.getUser());
        input.append(":");
        input.append(spa.getPassword());
        input.append("\n");
        wo.add(runWithPipe(true, input.toString(),
                           "Changing password for user " + spa.getUser(),
                           CHPASSWD));

        return wo;
    }
}
