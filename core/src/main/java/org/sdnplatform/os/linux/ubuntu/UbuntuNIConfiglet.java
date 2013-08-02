package org.sdnplatform.os.linux.ubuntu;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import static org.sdnplatform.os.ConfigletUtil.*;
import org.sdnplatform.os.IOSConfiglet;
import org.sdnplatform.os.WrapperOutput;
import org.sdnplatform.os.model.NetworkConfig;
import org.sdnplatform.os.model.NetworkInterface;
import org.sdnplatform.os.model.NetworkInterface.ConfigMode;
import org.sdnplatform.os.model.OSConfig;
import com.google.common.base.Objects;

/**
 * Configure network interfaces on linux
 * @author readams
 */
public class UbuntuNIConfiglet implements IOSConfiglet {
    protected static final String INTERFACES = "/etc/network/interfaces";
    private static final String[] RESTART = 
        {"/usr/sbin/invoke-rc.d", "networking", "restart"};
    private static final String IFDOWN = "/sbin/ifdown";
    private static final String IFUP = "/sbin/ifup";
    
    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.NETWORK_INTERFACES);
    }

    private String getIfaceName(NetworkInterface ni) {
        if ("Ethernet".equals(ni.getType())) {
            return "eth" + ni.getNumber();
        }
        return ni.getType() + ni.getNumber();
    }
    
    @Override
    public WrapperOutput applyConfig(File basePath, 
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        WrapperOutput wo = new WrapperOutput();
        NetworkConfig oldNetwork = null;
        NetworkConfig newNetwork = null;

        if (oldConfig != null && oldConfig.getNodeConfig() != null)
            oldNetwork = oldConfig.getNodeConfig().getNetworkConfig();
        if (newConfig != null && newConfig.getNodeConfig() != null)
            newNetwork = newConfig.getNodeConfig().getNetworkConfig();
        
        Map<String, NetworkInterface> oldMap = 
                new LinkedHashMap<String, NetworkInterface>();
        Map<String, NetworkInterface> newMap = 
                new LinkedHashMap<String, NetworkInterface>();

        if (oldNetwork != null &&
            oldNetwork.getNetworkInterfaces() != null) {
            for (NetworkInterface ni : oldNetwork.getNetworkInterfaces()) {
                oldMap.put(getIfaceName(ni), ni);
            }
        }
        if (newNetwork != null &&
            newNetwork.getNetworkInterfaces() != null) {
            for (NetworkInterface ni : newNetwork.getNetworkInterfaces()) {
                newMap.put(getIfaceName(ni), ni);
            }
        }
        
        StringBuilder ifaces = new StringBuilder();
        addEditWarning(ifaces, "#");
        ifaces.append("auto lo\niface lo inet loopback\n\n");
        
        ArrayList<String> todown = new ArrayList<String>(); 
        ArrayList<String> toup = new ArrayList<String>(); 
        
        for (Entry<String, NetworkInterface> e : newMap.entrySet()) {
            NetworkInterface ni = e.getValue();
            String name = e.getKey();
            ifaces.append("auto " + name + "\n");
            if (ConfigMode.DHCP.equals(ni.getMode())) {
                ifaces.append("iface " + name + " inet dhcp\n");
            } else {
                ifaces.append("iface " + name + " inet static\n");
                if (ni.getIpAddress() != null)
                    ifaces.append("    address " + ni.getIpAddress() + "\n");
                if (ni.getNetmask() != null)
                    ifaces.append("    netmask " + ni.getNetmask() + "\n");
                if (newNetwork.getDefaultGateway() != null)
                    ifaces.append("    gateway " + 
                                  newNetwork.getDefaultGateway() + "\n");
            }
            ifaces.append("\n");
            if (oldMap.containsKey(name)) {
                if (!oldMap.get(name).equals(ni) ||
                    !Objects.equal(oldNetwork.getDefaultGateway(), 
                                   newNetwork.getDefaultGateway())) { 
                    todown.add(name);
                    toup.add(name);
                }
                oldMap.remove(name);
            } else {
                toup.add(name);
            }
        }
        for (String name : oldMap.keySet()) {
            todown.add(name);
        }
        
        WrapperOutput updownwo = new WrapperOutput();
        boolean restart = false;
        for (String name : todown) {
            updownwo.add(run(false, IFDOWN, name));
        }
        wo.add(updownwo);
        if (!updownwo.succeeded()) restart = true;
        
        wo.add(writeConfigFile(basePath, INTERFACES, ifaces.toString()));
        updownwo = new WrapperOutput();
        for (String name : toup) {
            updownwo.add(run(false, IFUP, name));
        }
        wo.add(updownwo);
        if (!updownwo.succeeded()) restart = true;

        if (restart) {
            wo.add(run(RESTART));
        }
        
        return wo;
    }
}
