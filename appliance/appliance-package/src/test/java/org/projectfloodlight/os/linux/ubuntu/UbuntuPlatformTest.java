package org.projectfloodlight.os.linux.ubuntu;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;

import org.junit.Ignore;
import org.junit.Test;
import org.projectfloodlight.os.BasePlatformProviderT;
import org.projectfloodlight.os.ConfigletUtil;
import org.projectfloodlight.os.WrapperOutput;
import org.projectfloodlight.os.linux.ubuntu.UbuntuDNSConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuLoggingConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuLoginBannerConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuNIConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuNTPConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuPlatformProvider;
import org.projectfloodlight.os.linux.ubuntu.UbuntuRegenKeysActionlet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuSNMPConfiglet;
import org.projectfloodlight.os.linux.ubuntu.UbuntuTZConfiglet;
import org.projectfloodlight.os.model.LoggingConfig;
import org.projectfloodlight.os.model.LoggingServer;
import org.projectfloodlight.os.model.NetworkInterface;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.PowerAction;
import org.projectfloodlight.os.model.RegenKeysAction;
import org.projectfloodlight.os.model.SNMPConfig;
import org.projectfloodlight.os.model.SetPasswordAction;
import org.projectfloodlight.os.model.SetShellAction;
import org.projectfloodlight.os.model.TimeAction;
import org.projectfloodlight.os.model.NetworkInterface.ConfigMode;
import org.projectfloodlight.os.model.SetShellAction.Shell;

import static org.projectfloodlight.os.ConfigletUtil.*;

import com.google.common.collect.ImmutableMap;

public class UbuntuPlatformTest extends BasePlatformProviderT {
    protected static final String RELEASE_VERSION =
            "UnitTest OS 1.0.0 (build 42)\n";
            
    public void setupHook() throws Exception {
        File lsbRelease = 
                new File(basePath.getRoot(), 
                         UbuntuPlatformProvider.LSB_RELEASE);
        lsbRelease.getParentFile().mkdirs();
        try (BufferedWriter br = 
                new BufferedWriter(new FileWriter(lsbRelease))) {
            br.write("DISTRIB_ID=Ubuntu\n");
            br.write("DISTRIB_RELEASE=11.04");
        }
        
        File bsRelease = new File(basePath.getRoot(), ConfigletUtil.RELEASE);
        bsRelease.getParentFile().mkdirs();
        try (BufferedWriter br = 
                new BufferedWriter(new FileWriter(bsRelease))) {
            br.write(RELEASE_VERSION);
        }

        File snmpDefault = new File(basePath.getRoot(), 
                                  UbuntuSNMPConfiglet.SNMP_DEFAULT);
        snmpDefault.getParentFile().mkdirs();
        try (BufferedWriter br = new BufferedWriter(new FileWriter(snmpDefault))) {
            br.write("SNMPDRUN=no\nSOMETHINGELSE='blah blah'");
        }
        
        configManager.init(fmc);
    }
    
    @Test
    public void testReapply() throws Exception {
        baseApply();

        WrapperOutput wo = configManager.applyConfiguration(osConfig);
        logOutput(wo);
        assertTrue(wo.nonfatal());
        assertEquals(0, wo.getItems().size());
    }
    
    @Test
    public void testNetwork() throws Exception {
        baseApply();
        checkFileContents(UbuntuNIConfiglet.INTERFACES,
                          "# WARNING: Automanaged file.  Do not edit\n\n" + 
                          "auto lo\niface lo inet loopback\n\n" + 
                          "auto eth0\niface eth0 inet dhcp\n\n");
        
        NetworkInterface eth0 = new NetworkInterface();
        eth0.setType("Ethernet");
        eth0.setNumber(0);
        eth0.setMode(ConfigMode.STATIC);
        eth0.setIpAddress("192.168.1.2");
        eth0.setNetmask("255.255.255.0");

        NetworkInterface eth1 = new NetworkInterface();
        eth1.setType("Ethernet");
        eth1.setNumber(1);
        eth1.setMode(ConfigMode.DHCP);

        osConfig.getNodeConfig().getNetworkConfig().
            setDefaultGateway("192.168.1.1");

        NetworkInterface[] ifaces = {eth0, eth1};
        osConfig.getNodeConfig().
            getNetworkConfig().setNetworkInterfaces(ifaces);

        verifyOutput(expectedOutput("Executing /sbin/ifdown eth0",
                                    "Write to",
                                    "Executing /sbin/ifup eth0",
                                    "Executing /sbin/ifup eth1"),
                     baseApply());
        checkFileContents(UbuntuNIConfiglet.INTERFACES,
                          "# WARNING: Automanaged file.  Do not edit\n\n" + 
                          "auto lo\niface lo inet loopback\n\n" + 
                          "auto eth0\n" + 
                          "iface eth0 inet static\n" + 
                          "    address 192.168.1.2\n" + 
                          "    netmask 255.255.255.0\n" + 
                          "    gateway 192.168.1.1\n\n" + 
                          "auto eth1\niface eth1 inet dhcp\n\n");

        osConfig.getNodeConfig().getNetworkConfig().setNetworkInterfaces(null);
        baseApply();
        checkFileContents(UbuntuNIConfiglet.INTERFACES,
                          "# WARNING: Automanaged file.  Do not edit\n\n" + 
                          "auto lo\niface lo inet loopback\n\n");
    }
    
    @Test
    public void testDNS() throws Exception {
        osConfig.getNodeConfig().getNetworkConfig().
            setNetworkInterfaces(new NetworkInterface[0]);
        baseApply();        
        checkFileContents(UbuntuDNSConfiglet.RESOLV,
                          "# WARNING: Automanaged file.  Do not edit\n\n" + 
                          "nameserver 8.8.8.8\n" + 
                          "search example.com\n");

        osConfig.getNodeConfig().getNetworkConfig().
            setDnsServers(new String[] {"8.8.8.8", "1.2.3.4"});
        
        verifyOutput(expectedOutput("Write to"),
                     baseApply());
        checkFileContents(UbuntuDNSConfiglet.RESOLV,
                          "# WARNING: Automanaged file.  Do not edit\n\n" + 
                          "nameserver 8.8.8.8\n" + 
                          "nameserver 1.2.3.4\n" + 
                          "search example.com\n");

        osConfig.getNodeConfig().getNetworkConfig().
            setDomainLookupsEnabled(false);
        
        verifyOutput(expectedOutput("Write to"),
                     baseApply());
        checkFileContents(UbuntuDNSConfiglet.RESOLV,
                          "# WARNING: Automanaged file.  Do not edit\n\n");
    }
    
    @Test
    public void testNTP() throws Exception {
        baseApply();
        String t = templateSubst(UbuntuNTPConfiglet.TEMPLATE,
                                 ImmutableMap.of("<SERVERS>", 
                                                 "server 0.pool.ntp.org\n" +
                                                 "server 1.pool.ntp.org\n"));
        checkFileContents(UbuntuNTPConfiglet.NTP_CONF, t);
        
        osConfig.getNodeConfig().getTimeConfig().
            setNtpServers(new String[]{"example.com"});
        verifyOutput(expectedOutput("Write template",
                                    "Executing /usr/sbin/invoke-rc.d ntp restart"),
                     baseApply());
        t = templateSubst(UbuntuNTPConfiglet.TEMPLATE,
                          ImmutableMap.of("<SERVERS>",
                                          "server example.com\n"));
        checkFileContents(UbuntuNTPConfiglet.NTP_CONF, t);

        osConfig.getNodeConfig().getTimeConfig().
            setNtpServers(null);
        verifyOutput(expectedOutput("Write template",
                                    "Executing /usr/sbin/invoke-rc.d ntp restart"),
                     baseApply());
        t = templateSubst(UbuntuNTPConfiglet.TEMPLATE,
                          ImmutableMap.of("<SERVERS>", ""));
        checkFileContents(UbuntuNTPConfiglet.NTP_CONF, t);
    }

    @Test
    public void testTimeZone() throws Exception {
        baseApply();
        checkFileContents(UbuntuTZConfiglet.TIMEZONE, 
                          "UTC\n");
        
        osConfig.getNodeConfig().getTimeConfig().
            setTimeZone("America/Los_Angeles");
        verifyOutput(expectedOutput("Write to",
                                    "Executing /usr/sbin/dpkg-reconfigure -f noninteractive tzdata",
                                    "Executing /sbin/initctl restart rsyslog"),
                     baseApply());
        checkFileContents(UbuntuTZConfiglet.TIMEZONE, 
                          "America/Los_Angeles\n");
    }

    @Test
    public void testLogging() throws Exception {
        baseApply();
        String base = "# WARNING: Automanaged file.  Do not edit\n\n";
        checkFileContents(UbuntuLoggingConfiglet.REMOTE_SYSLOG, base);
        
        LoggingConfig lc = new LoggingConfig();
        lc.setLoggingEnabled(true);
        LoggingServer ls1 = new LoggingServer();
        ls1.setLogLevel("info");
        ls1.setServer("10.10.10.10");
        lc.setLoggingServers(new LoggingServer[] {ls1});
        osConfig.getNodeConfig().setLoggingConfig(lc);
        
        verifyOutput(expectedOutput("Write to",
                                    "Executing /sbin/initctl restart rsyslog"),
                     baseApply());   
        base += "*.info @10.10.10.10\n";
        checkFileContents(UbuntuLoggingConfiglet.REMOTE_SYSLOG, base);

        LoggingServer ls2 = new LoggingServer();
        ls2.setLogLevel("warning");
        ls2.setServer("10.10.10.11");
        lc.setLoggingServers(new LoggingServer[] {ls1, ls2});

        verifyOutput(expectedOutput("Write to",
                                    "Executing /sbin/initctl restart rsyslog"),
                     baseApply());
        base += "*.warning @10.10.10.11\n";
        checkFileContents(UbuntuLoggingConfiglet.REMOTE_SYSLOG, base);
    }

    @Test
    public void testLoginBanner() throws Exception {
        baseApply();
        osConfig.getGlobalConfig().setLoginBanner("This is a test");
        baseApply();
        checkFileContents(UbuntuLoginBannerConfiglet.ISSUE, 
                          "This is a test\n");
    }
    
    @Test
    public void testSNMP() throws Exception {
        baseApply();
        String base = "# WARNING: Automanaged file.  Do not edit\n\n" +
                "agentAddress udp:161,udp6:[::1]:161\n" +
                "sysDescr " + RELEASE_VERSION + 
                "sysObjectID .1.3.6.1.4.1.37538.1\n";
        checkFileContents(UbuntuSNMPConfiglet.SNMP_CONF, 
                          base);
        checkDefaultFile(UbuntuSNMPConfiglet.SNMP_DEFAULT, 
                         ImmutableMap.of("SNMPDRUN", "no", 
                                         "SOMETHINGELSE", "blah blah"));
        
        SNMPConfig c = new SNMPConfig();
        osConfig.getGlobalConfig().setSnmpConfig(c);
        c.setEnabled(true);
        c.setCommunity("public");
        c.setLocation("In the cloud");
        c.setContact("example@example.com");
        verifyOutput(expectedOutput("Write to ",
                                    "Edit defaults file ",
                                    "Executing /usr/sbin/invoke-rc.d snmpd restart"),
                     baseApply());
        checkFileContents(UbuntuSNMPConfiglet.SNMP_CONF, 
                          base +
                          "rocommunity public\n" +
                          "sysLocation In the cloud\n" +
                          "sysContact example@example.com\n");
        checkDefaultFile(UbuntuSNMPConfiglet.SNMP_DEFAULT, 
                         ImmutableMap.of("SNMPDRUN", "yes"));
    }

    @Test
    @Ignore
    //  XXX - TODO
    public void testFirewall() throws Exception {
        fail();

    }
    
    @Test
    @Ignore
    //  XXX - TODO
    public void testHostname() throws Exception {
        fail();

    }
    
    @Test
    public void testActionNTPDate() throws Exception {
        OSAction action = new OSAction();
        action.setTimeAction(new TimeAction());
        action.getTimeAction().setNtpServer("ntp.example.com");
        verifyOutput(expectedOutput("Executing /usr/sbin/invoke-rc.d ntp stop",
                                    "Executing /usr/sbin/ntpdate ntp.example.com",
                                    "Executing /usr/sbin/invoke-rc.d ntp start"),
                     applyAction(action));
    }
    
    @Test
    public void testActionSetTime() throws Exception {
        OSAction action = new OSAction();
        action.setTimeAction(new TimeAction());
        action.getTimeAction().setSystemTime(new Date(1372284332000L));
        verifyOutput(expectedOutput("Executing /bin/date -s \"Wed Jun 26 22:05:32 UTC 2013\""),
                     applyAction(action));
    }

    @Test
    public void testActionPower() throws Exception {
        OSAction action = new OSAction();
        action.setPowerAction(new PowerAction());
        action.getPowerAction().setAction(PowerAction.Action.REBOOT);
        verifyOutput(expectedOutput("Executing /sbin/reboot"),
                     applyAction(action));

        action.getPowerAction().setAction(PowerAction.Action.SHUTDOWN);
        verifyOutput(expectedOutput("Executing /sbin/halt"),
                     applyAction(action));
    }

    @Test
    public void testActionRegenKeys() throws Exception {
        ArrayList<String> expected = new ArrayList<>();
        for (String k : UbuntuRegenKeysActionlet.SSH_KEYS) {
            File f = new File(basePath.getRoot(), k);
            f.getParentFile().mkdirs();
            f.createNewFile();
            expected.add("Deleting " + k);
        }
        
        expected.add("Executing /usr/sbin/dpkg-reconfigure -f noninteractive openssh-server");
        expected.add("Executing /usr/sbin/make-ssl-cert generate-default-snakeoil --force-overwrite");
        expected.add("Executing /usr/sbin/invoke-rc.d nginx restart");
        
        OSAction action = new OSAction();
        action.setRegenKeysAction(new RegenKeysAction());
        RegenKeysAction.Action[] as = {RegenKeysAction.Action.SSH, 
                                       RegenKeysAction.Action.WEB_SSL};
        action.getRegenKeysAction().setActions(as);
        
        verifyOutput(expectedOutput(expected.toArray(new String[0])),
                     applyAction(action));
    }
    
    @Test
    public void testActionSetShell() throws Exception {
        OSAction action = new OSAction();
        action.setSetShellAction(new SetShellAction());
        action.getSetShellAction().setUser("admin");
        action.getSetShellAction().setShell(Shell.FIRSTBOOT);
        verifyOutput(expectedOutput("Executing /usr/sbin/usermod --shell /usr/bin/floodlight-firstboot admin"),
                     applyAction(action));

        action.getSetShellAction().setShell(Shell.CLI);
        verifyOutput(expectedOutput("Executing /usr/sbin/usermod --shell /usr/bin/floodlight-login admin"),
                     applyAction(action));
    }

    @Test
    public void testActionSetPass() throws Exception {
        OSAction action = new OSAction();
        action.setSetPasswordAction(new SetPasswordAction());
        action.getSetPasswordAction().setUser("recovery");
        action.getSetPasswordAction().setPassword("an excellent password");
        verifyOutput(expectedOutput("Executing /usr/sbin/chpasswd"),
                     applyAction(action));
    }
}
