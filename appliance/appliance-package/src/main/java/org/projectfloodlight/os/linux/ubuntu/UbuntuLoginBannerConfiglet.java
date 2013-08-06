package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSConfig;

import static org.projectfloodlight.os.ConfigletUtil.*;

public class UbuntuLoginBannerConfiglet implements IOSConfiglet {
    protected static final String ISSUE = "/etc/issue";
    protected static final String ISSUE_NET = "/etc/issue.net";

    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.LOGIN_BANNER);
    }

    @Override
    public WrapperOutput applyConfig(File basePath,
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        String loginBanner = newConfig.getGlobalConfig().getLoginBanner();
        if (loginBanner == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(getVersionStr(basePath));
            sb.append("\nLog in as 'admin' to configure\n\n");
            loginBanner = sb.toString();
        } else {
            loginBanner += "\n";
        }

        loginBanner = loginBanner.replace("\\n", "\n");

        WrapperOutput wo = new WrapperOutput();
        wo.add(writeConfigFile(basePath, ISSUE, loginBanner));
        wo.add(writeConfigFile(basePath, ISSUE_NET, loginBanner));
        return wo;
    }
}
