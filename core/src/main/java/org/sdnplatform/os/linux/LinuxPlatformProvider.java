package org.sdnplatform.os.linux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.sdnplatform.os.AbstractPlatformProvider;
import org.sdnplatform.os.IOSActionlet;
import org.sdnplatform.os.IOSConfiglet;
import org.sdnplatform.os.OSConfigWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for linux-based system configuration
 * @author readams
 */
public abstract class LinuxPlatformProvider extends AbstractPlatformProvider {
    protected static final Logger logger =
            LoggerFactory.getLogger(LinuxPlatformProvider.class.getName());
    
    // *****************
    // IPlatformProvider
    // *****************

    public List<String> getWrapperArgs(String privedWrapper,
                                       String basePath, 
                                       String cachePath,
                                       boolean runWithPrivs,
                                       boolean dryRun,
                                       boolean action) {
        String classPath = System.getProperty("java.class.path");
        ArrayList<String> args = new ArrayList<String>();
        if (runWithPrivs) {
            args.add("sudo");
            args.add("-n");
        }
        args.addAll(Arrays.asList(privedWrapper, 
                                  classPath, 
                                  OSConfigWrapper.class.getCanonicalName(),
                                  "--provider", getClass().getCanonicalName(),
                                  "--basePath", basePath,
                                  "--cachePath", cachePath));
        if (dryRun)
            args.add("--dryRun");
        if (action)
            args.add("--action");
        return args;
    }

    // ************************
    // AbstractPlatformProvider
    // ************************

    @Override
    protected Collection<IOSConfiglet> getConfiglets() {
        ArrayList<IOSConfiglet> clets = new ArrayList<IOSConfiglet>();
        return clets;
    }
    
    @Override
    protected Collection<IOSActionlet> getActionlets() {
        ArrayList<IOSActionlet> alets = new ArrayList<IOSActionlet>();
        return alets;
    }
}
