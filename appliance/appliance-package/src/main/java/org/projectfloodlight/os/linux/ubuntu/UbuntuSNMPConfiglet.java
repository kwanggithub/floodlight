package org.projectfloodlight.os.linux.ubuntu;

import java.io.File;
import java.util.EnumSet;

import org.projectfloodlight.os.IOSConfiglet;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.model.OSConfig;
import org.projectfloodlight.os.model.SNMPConfig;

import com.google.common.collect.ImmutableMap;

import static org.projectfloodlight.os.ConfigletUtil.*;

/**
 * Configure SNMPd as an SNMP client
 * @author readams
 */
public class UbuntuSNMPConfiglet implements IOSConfiglet {
    static final String SNMP_CONF = "/etc/snmp/snmpd.conf";
    static final String SNMP_DEFAULT = "/etc/default/snmpd";

    static final String[] SNMP_RESTART = 
        {"/usr/sbin/invoke-rc.d", "snmpd", "restart"};

    @Override
    public EnumSet<ConfigType> provides() {
        return EnumSet.of(ConfigType.SNMP);
    }

    @Override
    public WrapperOutput applyConfig(File basePath, 
                                     OSConfig oldConfig,
                                     OSConfig newConfig) {
        WrapperOutput wo = new WrapperOutput();

        SNMPConfig c = null;
        try {
            c = newConfig.getGlobalConfig().getSnmpConfig();
        } catch (NullPointerException e) {}  
        if (c == null) c = new SNMPConfig();
        
        StringBuilder sb = new StringBuilder();
        addEditWarning(sb, "#");
        sb.append("agentAddress udp:161,udp6:[::1]:161\n");
        sb.append("sysDescr ");
        sb.append(getVersionStr(basePath));
        sb.append("\nsysObjectID .1.3.6.1.4.1.37538.1\n");
        if (c.getCommunity() != null) {
            sb.append("rocommunity ");
            sb.append(c.getCommunity());
            sb.append("\n");
        }
        if (c.getLocation() != null) {
            sb.append("sysLocation ");
            sb.append(c.getLocation());
            sb.append("\n");
        }
        if (c.getContact() != null) {
            sb.append("sysContact ");
            sb.append(c.getContact());
            sb.append("\n");
        }
        
        wo.add(writeConfigFile(basePath, SNMP_CONF, sb.toString()));
        wo.add(editDefaults(basePath, SNMP_DEFAULT, 
                            ImmutableMap.of("SNMPDRUN", 
                                            c.isEnabled() ? "yes" : "no")));
        wo.add(run(SNMP_RESTART));
        return wo;
    }

}
