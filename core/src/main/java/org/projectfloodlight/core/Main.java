/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package org.projectfloodlight.core;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.projectfloodlight.core.internal.CmdLineSettings;
import org.projectfloodlight.core.module.FloodlightModuleException;
import org.projectfloodlight.core.module.FloodlightModuleLoader;
import org.projectfloodlight.core.module.IFloodlightModuleContext;
import org.projectfloodlight.db.IBigDBService;
import org.projectfloodlight.util.BuildInfo;
import org.projectfloodlight.util.BuildInfoManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Host for the Floodlight main method
 * @author alexreimers
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Main method to load configuration and modules
     * @param args
     * @throws FloodlightModuleException
     */
    public static void main(String[] args) throws Exception {
        // Setup logger
        System.setProperty("org.restlet.engine.loggerFacadeClass",
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");

        for(BuildInfo info: BuildInfoManager.getInstance().getAllBuildInfos())
            logger.info(info.toString(true, true));

        CmdLineSettings settings = new CmdLineSettings();
        CmdLineParser parser = new CmdLineParser(settings);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            parser.printUsage(System.out);
            System.exit(1);
        }
        // Load modules
        FloodlightModuleLoader fml = new FloodlightModuleLoader();
        IFloodlightModuleContext moduleContext = 
                fml.loadModulesFromConfig(settings.getModuleFile());
        // Run BigDB server
        IBigDBService bigDBService = moduleContext.getServiceImpl(IBigDBService.class);
        if (bigDBService != null)
            bigDBService.run();
        // Run the main floodlight module
        IFloodlightProviderService controller =
                moduleContext.getServiceImpl(IFloodlightProviderService.class);
        // This call blocks, it has to be the last line in the main
        controller.run();
    }
}
