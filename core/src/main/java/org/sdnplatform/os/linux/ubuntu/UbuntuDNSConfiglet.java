package org.sdnplatform.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.python.google.common.base.Joiner;
import org.sdnplatform.os.IOSConfiglet;
import org.sdnplatform.os.WrapperOutput;
import org.sdnplatform.os.model.NetworkConfig;
import org.sdnplatform.os.model.NetworkInterface;
import org.sdnplatform.os.model.NetworkInterface.ConfigMode;
import org.sdnplatform.os.model.OSConfig;

import static org.sdnplatform.os.ConfigletUtil.*;

public class UbuntuDNSConfiglet implements IOSConfiglet {
    protected static final String RESOLV = "/etc/resolv.conf";

    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.NETWORK_DNS);
    }

    @Override
    public WrapperOutput applyConfig(File basePath, OSConfig oldConfig,
                                     OSConfig newConfig) {
        NetworkConfig newNetwork = null;
        try {
            newNetwork = newConfig.getNodeConfig().getNetworkConfig();
        } catch (NullPointerException e) {}
        
        boolean hasDHCP = false;
        if (newNetwork != null) {
            NetworkInterface[] nis = newNetwork.getNetworkInterfaces();
            if (nis != null) {
                for (NetworkInterface ni : nis) {
                    if (ConfigMode.DHCP.equals(ni.getMode()))
                        hasDHCP = true;
                }
            }
        }

        if (hasDHCP)
            return new WrapperOutput();
        
        boolean enabled = false;
        String[] servers = new String[] {};
        String[] domainName = null;
        if (newNetwork != null) {
            enabled = newNetwork.isDomainLookupsEnabled();
            servers = newNetwork.getDnsServers();
            domainName = newNetwork.getDnsSearchPath();
        }
        StringBuilder resolv = new StringBuilder();
        addEditWarning(resolv, "#");
        if (enabled) {
            for (String s : servers) {
                resolv.append("nameserver " + s + "\n");
            }
            if (domainName != null && domainName.length > 0) {
                resolv.append("search " + Joiner.on(' ').join(domainName) + "\n");
            }
        }
        return writeConfigFile(basePath, RESOLV, 
                               resolv.toString());
    }
}
