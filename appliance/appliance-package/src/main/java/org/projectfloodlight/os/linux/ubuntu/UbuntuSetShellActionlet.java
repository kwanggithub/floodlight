package org.projectfloodlight.os.linux.ubuntu;

import static org.projectfloodlight.os.ConfigletUtil.run;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSActionlet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.SetShellAction;

public class UbuntuSetShellActionlet implements IOSActionlet {
    private static final String USER_MOD = "/usr/sbin/usermod";
    private static final String FIRSTBOOT = "/usr/bin/floodlight-firstboot";
    private static final String CLI = "/usr/bin/floodlight-login";

    @Override
    public EnumSet<ActionType> provides() {
        return EnumSet.of(ActionType.SET_SHELL);
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput wo = new WrapperOutput();
        SetShellAction ssa = action.getSetShellAction();
        String shell;
        switch (ssa.getShell()) {
            case FIRSTBOOT:
                shell = FIRSTBOOT;
                break;
            case CLI:
            default:
                shell = CLI;
                break;
        }
        wo.add(run(USER_MOD, "--shell", shell, ssa.getUser()));
        return wo;
    }
}
