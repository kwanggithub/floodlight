package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.LoggingConfig;
import org.projectfloodlight.os.model.LoggingServer;
import org.projectfloodlight.os.model.OSConfig;

import static org.projectfloodlight.os.ConfigletUtil.*;

public class UbuntuLoggingConfiglet implements IOSConfiglet {
    static final String REMOTE_SYSLOG = "/etc/rsyslog.d/99-remote";
    static final String[] SYSLOG_RESTART = 
        {"/sbin/initctl", "restart", "rsyslog"};

    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.LOGGING);
    }

    @Override
    public WrapperOutput applyConfig(File basePath,
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        WrapperOutput wo = new WrapperOutput();
        LoggingConfig lg = null;
        try {
            lg = newConfig.getNodeConfig().getLoggingConfig();
        } catch (NullPointerException e) {}
        if (lg == null) lg = new LoggingConfig();
                
        StringBuilder sb = new StringBuilder();
        addEditWarning(sb, "#");
        if (lg.isLoggingEnabled()) {
            for (LoggingServer ls : lg.getLoggingServers()) {
                if (ls.getServer() == null) continue;
                sb.append("*.");
                sb.append(ls.getLogLevel());
                sb.append(" @");
                sb.append(ls.getServer());
                sb.append("\n");
            }
        }
        wo.add(writeConfigFile(basePath, REMOTE_SYSLOG, sb.toString()));
        wo.add(run(SYSLOG_RESTART));
        return wo;
    }
}
