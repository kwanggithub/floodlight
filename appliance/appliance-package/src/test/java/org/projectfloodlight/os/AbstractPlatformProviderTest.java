package org.projectfloodlight.os;

import static org.junit.Assert.*;

import java.util.Date;
import java.util.EnumSet;

import org.junit.Test;
import org.projectfloodlight.os.AbstractPlatformProvider;
import org.projectfloodlight.os.IOSActionlet.ActionType;
import org.projectfloodlight.os.IOSConfiglet.ConfigType;
import org.projectfloodlight.os.model.ControllerNode;
import org.projectfloodlight.os.model.GlobalConfig;
import org.projectfloodlight.os.model.NetworkConfig;
import org.projectfloodlight.os.model.NetworkInterface;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.OSConfig;
import org.projectfloodlight.os.model.SetShellAction;
import org.projectfloodlight.os.model.TimeAction;
import org.projectfloodlight.os.model.SetShellAction.Shell;

public class AbstractPlatformProviderTest {
    @Test
    public void testFindChanges() throws Exception {
        OSConfig oldc = null;
        OSConfig newc = BasePlatformProviderT.defaultConfig();
        EnumSet<ConfigType> changes = EnumSet.noneOf(ConfigType.class);
        
        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.allOf(ConfigType.class), changes);
        
        changes.clear();
        oldc = newc;
        newc = BasePlatformProviderT.defaultConfig();
        newc.getNodeConfig().getNetworkConfig().setDefaultGateway("1.2.3.4");
        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.of(ConfigType.NETWORK_INTERFACES), changes);

        changes.clear();
        newc.getNodeConfig().getTimeConfig().setTimeZone("America/Los_Angeles");
        newc.getNodeConfig().getNetworkConfig().
            getNetworkInterfaces()[0].setIpAddress("4.3.2.1");
        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.of(ConfigType.NETWORK_INTERFACES, 
                                ConfigType.NETWORK_FIREWALL,
                                ConfigType.TIME_ZONE), changes);

        changes.clear();
        newc = BasePlatformProviderT.defaultConfig();
        newc.getNodeConfig().getTimeConfig().setTimeZone("America/Los_Angeles");
        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.of(ConfigType.TIME_ZONE), changes);

        changes.clear();
        oldc = BasePlatformProviderT.defaultConfig();
        newc = BasePlatformProviderT.defaultConfig();
        newc.getNodeConfig().getNetworkConfig().
            setNetworkInterfaces(new NetworkInterface[0]);
        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.of(ConfigType.NETWORK_INTERFACES, 
                                ConfigType.NETWORK_FIREWALL), changes);

        changes.clear();
        oldc = new OSConfig();
        newc = new OSConfig();
        newc.setGlobalConfig(new GlobalConfig());
        newc.setNodeConfig(new ControllerNode());
        NetworkConfig nc = new NetworkConfig();
        nc.setNetworkInterfaces(new NetworkInterface[0]);
        newc.getNodeConfig().setNetworkConfig(nc);

        AbstractPlatformProvider.findChanges(changes, OSConfig.class,
                                             oldc, newc, null);
        assertEquals(EnumSet.of(ConfigType.NETWORK_INTERFACES, 
                                ConfigType.NETWORK_FIREWALL,
                                ConfigType.NETWORK_DNS), changes);
    }
    
    @Test
    public void testFindActions() throws Exception {
        OSAction action = new OSAction();
        action.setSetShellAction(new SetShellAction());
        action.getSetShellAction().setUser("admin");
        action.getSetShellAction().setShell(Shell.CLI);
        EnumSet<ActionType> actions = EnumSet.noneOf(ActionType.class);

        AbstractPlatformProvider.findActions(actions, OSAction.class, 
                                             action, null);
        assertEquals(EnumSet.of(ActionType.SET_SHELL), actions);
        
        actions.clear();
        action = new OSAction();
        action.setTimeAction(new TimeAction());
        action.getTimeAction().setSystemTime(new Date(1372284332));

        AbstractPlatformProvider.findActions(actions, OSAction.class, 
                                             action, null);
        assertEquals(EnumSet.of(ActionType.SET_TIME), actions);
    }

}
