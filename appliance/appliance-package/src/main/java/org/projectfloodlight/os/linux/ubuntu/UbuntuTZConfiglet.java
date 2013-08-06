package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import static org.projectfloodlight.os.ConfigletUtil.*;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSConfig;

public class UbuntuTZConfiglet implements IOSConfiglet {
    protected static final String TIMEZONE = "/etc/timezone";
    static final String[] RECONFIGURE = 
        {"/usr/sbin/dpkg-reconfigure", "-f", "noninteractive", "tzdata"};
    
    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.TIME_ZONE);
    }

    @Override
    public WrapperOutput applyConfig(File basePath,
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        String zone = null;        
        try {
            zone = newConfig.getNodeConfig().getTimeConfig().getTimeZone();
        } catch (NullPointerException e) {}
        if (zone == null) zone = "UTC";
        zone += "\n";

        WrapperOutput wo = new WrapperOutput();
        wo.add(writeConfigFile(basePath, TIMEZONE, zone));
        wo.add(run(RECONFIGURE));
        wo.add(run(UbuntuLoggingConfiglet.SYSLOG_RESTART));
        return wo;
    }
}
