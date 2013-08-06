package org.projectfloodlight.os.linux.ubuntu;

import static org.projectfloodlight.os.ConfigletUtil.run;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.EnumSet;
import java.util.TimeZone;

import org.projectfloodlight.os.IOSActionlet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.TimeAction;

public class UbuntuTimeActionlet implements IOSActionlet {
    static final String[] NTP_STOP = 
        {"/usr/sbin/invoke-rc.d", "ntp", "stop"};
    static final String[] NTP_START = 
        {"/usr/sbin/invoke-rc.d", "ntp", "start"};
    static final String NTP_DATE = "/usr/sbin/ntpdate";
    static final String DATE = "/bin/date";

    @Override
    public EnumSet<ActionType> provides() {
        return EnumSet.of(ActionType.NTP_DATE, ActionType.SET_TIME);
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput wo = new WrapperOutput();

        TimeAction t = action.getTimeAction();
        if (t.getNtpServer() != null) {
            wo.add(run(NTP_STOP));
            wo.add(run(NTP_DATE, t.getNtpServer()));
            wo.add(run(NTP_START));
        }
        if (t.getSystemTime() != null) {
            // Wed Jun 26 14:40:33 UTC 2013
            SimpleDateFormat f = 
                    new SimpleDateFormat("\"EEE MMM dd HH:mm:ss z yyyy\"");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            wo.add(run(DATE, "-s", f.format(t.getSystemTime())));
        }

        return wo;
    }

}
