package org.projectfloodlight.db.auth.application;

import java.io.File;
import java.io.IOException;

import org.projectfloodlight.db.auth.AuthConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** utility class to construct a registry from an authconfig parameter
 *
 * @author Carl Roth <carl.roth@bigswitch.com>
 *
 */
public class ApplicationRegistryConfig {

    protected final static Logger logger = LoggerFactory.getLogger(ApplicationRegistryConfig.class);

    /** consruct a JsonApplicationRegistry using hints from the floodlight properties
     *
     * @param config
     * @return
     */
    public static ApplicationRegistry fromAuthConfig(AuthConfig config) {
        ApplicationRegistry registry = new JsonApplicationRegistry();
        String configProperty = config.getParam(AuthConfig.APPLICATIONS);
        if (configProperty == null) {
            logger.warn("missing property {}", AuthConfig.APPLICATIONS.getSysProp());
            return registry;
        }
        File defaultDir = config.getParam(AuthConfig.APPLICATION_DIR);
        String[] appConfigs = configProperty.split("[,]");
        for (String appConfig : appConfigs) {
            String[] configWords = appConfig.split("=");

            String appName;
            File appPath;
            String appKey;
            if (configWords.length == 1) {
                appName = configWords[0];
                appPath = new File(defaultDir, appName + ".json");
                appKey = appName;
            } else if (configWords.length > 1) {
                appName = configWords[0];
                appPath = new File(configWords[1]);
                if (!appPath.getName().endsWith(".json")) {
                    logger.warn("invalid application config path for {}: {}", appName, appPath);
                    continue;
                }
                appKey = appPath.getName().substring(0, appPath.getName().length()-5);
            } else {
                logger.warn("invalid application config {}: {}", config, appConfig);
                continue;
            }
            logger.info("initializing app key for {} in {}", appName, appPath);

            ApplicationRegistration reg = new ApplicationRegistration(appName);
            registry.registerApplication(reg);

            File storeDir = appPath.getParentFile();
            try {
                JsonApplicationRegistry.saveJson(storeDir, appKey, reg);
            } catch (IOException e) {
                logger.warn("cannot write secret to {}", appPath.getPath());
            }

        }
        return registry;
    }

}
