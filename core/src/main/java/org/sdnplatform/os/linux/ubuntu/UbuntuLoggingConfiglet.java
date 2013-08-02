package org.sdnplatform.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.sdnplatform.os.IOSConfiglet;
import org.sdnplatform.os.WrapperOutput;
import org.sdnplatform.os.model.LoggingConfig;
import org.sdnplatform.os.model.LoggingServer;
import org.sdnplatform.os.model.OSConfig;
import static org.sdnplatform.os.ConfigletUtil.*;

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
