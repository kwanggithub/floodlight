package org.sdnplatform.os.linux.ubuntu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.sdnplatform.os.IOSActionlet;
import org.sdnplatform.os.IOSConfiglet;
import org.sdnplatform.os.linux.LinuxPlatformProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Platform provider for Ubuntu
 * @author readams
 */
public class UbuntuPlatformProvider extends LinuxPlatformProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(UbuntuPlatformProvider.class.getName());
    
    protected static final String LSB_RELEASE = "/etc/lsb-release";

    // *****************
    // IPlatformProvider
    // *****************

    @Override
    public boolean isApplicable(File basePath) {
        BufferedReader lsbRelease;
        File lsbFile = new File(basePath, LSB_RELEASE);
        try {
            lsbRelease = Files.newReader(lsbFile, StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            logger.debug("{} not applicable: {} not found", 
                         UbuntuPlatformProvider.class.getName(), 
                         lsbFile);
            return false;
        }
        
        String line;
        boolean id = false;
        String releaseStr = null;
        boolean release = false;
        
        try (BufferedReader br = new BufferedReader(lsbRelease)) {
            while (null != (line = br.readLine())) {
                String[] kv = line.split("=");
                if ("DISTRIB_ID".equals(kv[0])) {
                    id = "Ubuntu".equals(kv[1]);
                }
                if ("DISTRIB_RELEASE".equals(kv[0])) {
                    releaseStr = kv[1];
                    String[] version = releaseStr.split("\\.");
                    release = "11".compareTo(version[0]) <= 0;
                }
            }
        } catch (IOException e) {
            logger.debug("{} not applicable: Could not read from {}", 
                         UbuntuPlatformProvider.class.getName(), 
                         LSB_RELEASE);
            return false;
        } 

        if (id) {
            if (release) {
                logger.debug("Detected supported OS: Ubuntu {}", releaseStr);
                return true;
            } else {
                logger.debug("Unsupported Ubuntu version: {}", releaseStr);
            }
        }
        return false;
    }

    // ************************
    // AbstractPlatformProvider
    // ************************

    @Override
    protected Collection<IOSConfiglet> getConfiglets() {
        Collection<IOSConfiglet> clets = super.getConfiglets();
        clets.add(new UbuntuNIConfiglet());
        clets.add(new UbuntuDNSConfiglet());
        clets.add(new UbuntuTZConfiglet());
        clets.add(new UbuntuNTPConfiglet());
        clets.add(new UbuntuLoggingConfiglet());
        clets.add(new UbuntuLoginBannerConfiglet());
        clets.add(new UbuntuSNMPConfiglet());
        return clets;
    }

    @Override
    protected Collection<IOSActionlet> getActionlets() {
        Collection<IOSActionlet> alets = super.getActionlets();
        alets.add(new UbuntuTimeActionlet());
        alets.add(new UbuntuPowerActionlet());
        alets.add(new UbuntuSetShellActionlet());
        alets.add(new UbuntuSetPassActionlet());
        alets.add(new UbuntuRegenKeysActionlet());
        return alets;
    }

}
