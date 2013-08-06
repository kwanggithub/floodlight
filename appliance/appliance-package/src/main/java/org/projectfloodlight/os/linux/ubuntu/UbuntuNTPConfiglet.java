package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSConfig;

import com.google.common.collect.ImmutableMap;

import static org.projectfloodlight.os.ConfigletUtil.*;

public class UbuntuNTPConfiglet implements IOSConfiglet {
    static final String TEMPLATE = "templates/ntp.conf";
    static final String NTP_CONF = "/etc/ntp.conf";
    static final String[] NTP_RESTART = 
        {"/usr/sbin/invoke-rc.d", "ntp", "restart"};

    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.TIME_NTP);
    }

    @Override
    public WrapperOutput applyConfig(File basePath, 
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        WrapperOutput wo = new WrapperOutput();
        String[] servers = null;
        try {
            servers = newConfig.getNodeConfig().getTimeConfig().getNtpServers();
        } catch (NullPointerException e) {}
        if (servers == null) servers = new String[] {};

        StringBuilder s = new StringBuilder();
        for (String server : servers) {
            s.append("server ");
            s.append(server);
            s.append("\n");
        }
        
        wo.add(writeTemplate(basePath, 
                             TEMPLATE, NTP_CONF, 
                             ImmutableMap.of("<SERVERS>", 
                                             s.toString())));
        wo.add(run(NTP_RESTART));
        return wo;
    }
}
