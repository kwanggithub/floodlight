package org.sdnplatform.os.linux.ubuntu;

import static org.sdnplatform.os.ConfigletUtil.run;

import java.io.File;
import java.util.EnumSet;

import org.sdnplatform.os.IOSActionlet;
import org.sdnplatform.os.WrapperOutput;
import org.sdnplatform.os.model.OSAction;
import org.sdnplatform.os.model.PowerAction;

public class UbuntuPowerActionlet implements IOSActionlet {
    private static final String HALT = "/sbin/halt";
    private static final String REBOOT = "/sbin/reboot";
    
    @Override
    public EnumSet<ActionType> provides() {
        return EnumSet.of(ActionType.POWER);
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput wo = new WrapperOutput();
        PowerAction pa = action.getPowerAction();
        switch (pa.getAction()) {
            case REBOOT:
                wo.add(run(REBOOT));
                break;
            case SHUTDOWN:
                wo.add(run(HALT));
                break;
        }
        return wo;
    }
}
