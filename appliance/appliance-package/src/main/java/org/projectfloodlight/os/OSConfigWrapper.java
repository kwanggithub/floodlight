package org.projectfloodlight.os;

import java.io.File;
import java.io.InputStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.projectfloodlight.os.WrapperOutput.Status;
import org.projectfloodlight.os.model.ControllerNode;
import org.projectfloodlight.os.model.GlobalConfig;
import org.projectfloodlight.os.model.NetworkConfig;
import org.projectfloodlight.os.model.NetworkInterface;
import org.projectfloodlight.os.model.OSAction;
import org.projectfloodlight.os.model.OSConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Privileged wrapper program accepts configuration from the unprivileged
 * main process and can apply the configuration to the underlying system.
 * @author readams
 */
public class OSConfigWrapper {
    private static ObjectMapper mapper = new ObjectMapper();
    private static ObjectReader osConfigReader = 
            mapper.reader(OSConfig.class);
    private static ObjectWriter osConfigWriter = 
            mapper.writerWithType(OSConfig.class);
    private static ObjectReader osActionReader = 
            mapper.reader(OSAction.class);
    private static ObjectWriter woWriter = 
            mapper.writerWithType(WrapperOutput.class);
    
    private static class WrapperSettings {
        @Option(name="--provider", required=true, 
                usage="Class name of the platform provider to use")
        protected String provider;

        @Option(name="--basePath", required=true, 
                usage="Base path to use.  Use '/' to apply to the real system")
        protected String basePath;

        @Option(name="--cachePath", required=true, 
                usage="Path to cache file relative to base")
        protected String cachePath;

        @Option(name="--default", 
                usage="Set to a default configuration")
        protected boolean setDefault;

        @Option(name="--action", 
                usage="Accept an OSAction rather than an OSConfig")
        protected boolean action;

        @Option(name="--dryRun", 
                usage="Don't really execute any subprocesses; just " + 
                      "simulate their success")
        protected boolean dryRun;
    }

    public static WrapperOutput apply(String[] args) {
        return apply(args, System.in);
    }

    public static WrapperOutput apply(String[] args, InputStream in) {
        WrapperSettings settings = new WrapperSettings();
        CmdLineParser parser = new CmdLineParser(settings);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            return WrapperOutput.error(Status.ARG_ERROR, e);
        }

        if (settings.dryRun)
            ConfigletUtil.setDryRun();
        
        IPlatformProvider provider;
        try {
            provider = (IPlatformProvider)Class.forName(settings.provider).
                            newInstance();
        } catch (Exception e) {
            return WrapperOutput.error(Status.ARG_ERROR, e);
        }

        if (settings.action) {
            return applyActions(settings, provider, in);
        } else {
            return applyConfig(settings, provider, in);
        }
    }
    
    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private static WrapperOutput applyConfig(WrapperSettings settings,
                                             IPlatformProvider provider,
                                             InputStream in) {
        OSConfig oldConfig = null;
        OSConfig newConfig;
        File basePath = new File(settings.basePath);
        File cache = new File(basePath, settings.cachePath);
        try {
            if (settings.setDefault) {
                newConfig = new OSConfig();
                newConfig.setGlobalConfig(new GlobalConfig());
                newConfig.setNodeConfig(new ControllerNode());
                NetworkConfig nc = new NetworkConfig();
                nc.setNetworkInterfaces(new NetworkInterface[0]);
                newConfig.getNodeConfig().setNetworkConfig(nc);
            } else {
                newConfig = osConfigReader.readValue(in);
            }
            if (cache.exists() && cache.canRead()) {
                oldConfig = osConfigReader.readValue(cache);
            }
        } catch (Exception e) {
            return WrapperOutput.error(Status.SERIALIZATION_ERROR, e);
        }

        // XXX - TODO - need to re-run validation of the configuration file
        // at this point for security to try to mitigate the damage from a 
        // compromised system DB process.
        
        try {
            WrapperOutput output = 
                    provider.applyConfiguration(basePath, oldConfig, newConfig);
            if (Status.SUCCESS.equals(output.getOverallStatus())) {
                try {
                    cache.getParentFile().mkdirs();
                    osConfigWriter.writeValue(cache, newConfig);
                } catch (Exception e) {
                    // Could not write out cached config value
                }
            }
            return output;
        } catch (Exception e) {
            return WrapperOutput.error(Status.GENERIC_ERROR, e);
        }
    }
    
    private static WrapperOutput applyActions(WrapperSettings settings,
                                              IPlatformProvider provider,
                                              InputStream in) {
        OSAction action;
        try {
            action = osActionReader.readValue(in);
        } catch (Exception e) {
            return WrapperOutput.error(Status.SERIALIZATION_ERROR, e);
        }
        
        try { 
            File basePath = new File(settings.basePath);
            WrapperOutput output = provider.applyAction(basePath, action);
            return output;
        } catch (Exception e) {
            return WrapperOutput.error(Status.GENERIC_ERROR, e);
        }
    }

    public static void main(String[] args) {
        try {
            WrapperOutput wo = apply(args);
            woWriter.writeValue(System.out, wo);
            if (!wo.succeeded()) System.exit(1);
        } catch (Exception e) {
            System.out.println("{\"status\": \"SUBPROCESS_ERROR\"," + 
                               "\"message\": \"Could not serialize output\"}");
            System.exit(2);
        }
    }
}
