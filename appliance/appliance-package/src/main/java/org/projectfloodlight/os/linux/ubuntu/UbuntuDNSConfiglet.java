package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.NetworkConfig;
import org.projectfloodlight.os.model.NetworkInterface;
import org.projectfloodlight.os.model.OSConfig;
import org.projectfloodlight.os.model.NetworkInterface.ConfigMode;
import org.python.google.common.base.Joiner;

import static org.projectfloodlight.os.ConfigletUtil.*;

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
