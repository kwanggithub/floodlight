package org.sdnplatform.os;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.bigdb.MockBigDBService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockThreadPoolService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.sdnplatform.os.WrapperOutput.Item;
import org.sdnplatform.os.WrapperOutput.Status;
import org.sdnplatform.os.model.ControllerNode;
import org.sdnplatform.os.model.GlobalConfig;
import org.sdnplatform.os.model.LoggingConfig;
import org.sdnplatform.os.model.NetworkConfig;
import org.sdnplatform.os.model.NetworkInterface;
import org.sdnplatform.os.model.NetworkInterface.ConfigMode;
import org.sdnplatform.os.model.OSAction;
import org.sdnplatform.os.model.OSConfig;
import org.sdnplatform.os.model.SNMPConfig;
import org.sdnplatform.os.model.TimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Tests for {@link OSConfigManager}
 * @author readams
 */
public abstract class BasePlatformProviderT {
    protected static Logger logger =
            LoggerFactory.getLogger(BasePlatformProviderT.class);
    
    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();
    
    protected OSConfigManager configManager;
    protected OSConfig osConfig;
    protected FloodlightModuleContext fmc = new FloodlightModuleContext();
    
    protected static OSConfig defaultConfig() {
        OSConfig oc = new OSConfig();
        ControllerNode cn = new ControllerNode();
        oc.setNodeConfig(cn);

        NetworkInterface ni = new NetworkInterface();
        ni.setType("Ethernet");
        ni.setNumber(0);
        ni.setMode(ConfigMode.DHCP);
        NetworkConfig nc = new NetworkConfig();
        nc.setDnsServers(new String[]{"8.8.8.8"});
        nc.setDomainLookupsEnabled(true);
        nc.setDomainName(new String[]{"example.com"});
        nc.setNetworkInterfaces(new NetworkInterface[]{ni});
        cn.setNetworkConfig(nc);
        
        LoggingConfig lc = new LoggingConfig();
        lc.setLoggingEnabled(false);
        cn.setLoggingConfig(lc);
        
        TimeConfig tc = new TimeConfig();
        tc.setTimeZone("UTC");
        tc.setNtpServers(new String[]{"0.pool.ntp.org", "1.pool.ntp.org"});
        cn.setTimeConfig(tc);
        
        SNMPConfig sc = new SNMPConfig();
        sc.setEnabled(false);
        
        oc.setGlobalConfig(new GlobalConfig());
        oc.getGlobalConfig().setLoginBanner("This is not a login banner");
        oc.getGlobalConfig().setSnmpConfig(sc);
        
        return oc;
    }
    
    public abstract void setupHook() throws Exception;

    @Before
    public void setUp() throws Exception {
        File tmpWrapper = basePath.newFile("oswrapper.sh");
        InputStream is = 
                this.getClass().getClassLoader().
                    getResourceAsStream("org/sdnplatform/os/oswrapper.sh");
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        String l;
        FileWriter w = new FileWriter(tmpWrapper);
        while (null != (l = br.readLine())) {
            w.write(l);
            w.write("\n");
        }
        br.close();
        w.flush();
        w.close();
        tmpWrapper.setExecutable(true, true);

        configManager = new OSConfigManager();
        MockBigDBService bigdb = new MockBigDBService();
        bigdb.addModuleSchema("floodlight", "2012-10-22");
        bigdb.addModuleSchema("os", "2013-06-12");
        fmc.addService(IThreadPoolService.class, new MockThreadPoolService());
        fmc.addService(IBigDBService.class, bigdb);
        fmc.addConfigParam(configManager, "basePath", 
                           basePath.getRoot().getAbsolutePath());
        fmc.addConfigParam(configManager, "cachePath", 
                           "etc/floodlight/osconfig.json");
        fmc.addConfigParam(configManager, "privedWrapper", 
                           tmpWrapper.getAbsolutePath());
        fmc.addConfigParam(configManager, "runWithPrivs", "false");
        fmc.addConfigParam(configManager, "dryRun", "true");
        bigdb.init(fmc);

        osConfig = defaultConfig();
        setupHook();
    }

    public static WrapperOutput expectedOutput(String... actions) {
        WrapperOutput wo = new WrapperOutput();
        for (String action : actions) {
            wo.addItem(new Item(Status.SUCCESS, action, null, null));
        }
        return wo;
    }
    
    public WrapperOutput baseApply() throws Exception {
        WrapperOutput wo = configManager.applyConfiguration(osConfig);
        logOutput(wo);
        assertTrue(wo.nonfatal());
        return wo;
    }
    
    public WrapperOutput applyAction(OSAction action) throws Exception {
        WrapperOutput wo = configManager.applyAction(action);
        logOutput(wo);
        assertTrue(wo.nonfatal());
        return wo;
    }

    public void checkFileContents(String path, String contents) 
            throws Exception {
        File config = new File(basePath.getRoot(), path);
        String configStr = Files.toString(config, Charset.defaultCharset());
        assertEquals(contents, configStr);
    }
    
    public void checkDefaultFile(String path, Map<String,String> expected) 
            throws Exception {
        File config = new File(basePath.getRoot(), path);
        BufferedReader br = new BufferedReader(new FileReader(config));
        Set<String> found = new HashSet<String>();
        try {
            String line;
            while (null != (line = br.readLine())) {
                Matcher m = ConfigletUtil.ETC_DEFAULT_PATTERN.matcher(line);
                if (m.matches() && expected.containsKey(m.group(1))) {
                    found.add(m.group(1));
                    String e = "'" + 
                            expected.get(m.group(1)).replace("'", "'\\''") + 
                            "'";
                    assertEquals(e, m.group(2));
                } 
            }
        } finally {
            br.close();
        }
        assertEquals(expected.size(), found.size());
    }
    
    public static void verifyOutput(WrapperOutput expected,
                                    WrapperOutput actual) throws Exception {
        List<Item> eitems = expected.getItems();
        List<Item> aitems = actual.getItems();
                
        for (int i = 0; i < Math.min(eitems.size(), aitems.size()); i++) {
            
            Item e = eitems.get(i);
            Item a = aitems.get(i);
            
            assertEquals(e.getStatus(), a.getStatus());
            assertTrue(a.getAction() + " startswith " + e.getAction(),
                       a.getAction().startsWith(e.getAction()));
        }
        
        if (eitems.size() > aitems.size()) {
            for (int i = aitems.size(); i < eitems.size(); i++) {
                logger.error("Missing " + eitems.get(i).getAction());
            }
        }

        if (aitems.size() > eitems.size()) {
            for (int i = eitems.size(); i < aitems.size(); i++) {
                logger.error("Extra " + aitems.get(i).getAction());
            }
        }
        assertEquals(eitems.size(), aitems.size());
    }
    
    public void logOutput(WrapperOutput wo) {
        if (wo.getItems().size() == 0) {
            logger.info("Result contained no items");
        }
        for (Item i : wo.getItems()) {
            if (Status.SUCCESS.equals(i.getStatus())) {
                logger.info("{}: {}", i.getStatus(), i.getAction());
            } else {
                logger.error("{}: {}\nFull Output:\n{}", 
                             new Object[]{i.getStatus(), 
                                          i.getMessage(), 
                                          i.getFullOutput()});
            }
        }
    }
}
