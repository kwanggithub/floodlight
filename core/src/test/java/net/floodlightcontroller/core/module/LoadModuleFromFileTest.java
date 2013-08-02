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

package net.floodlightcontroller.core.module;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;

import static org.junit.Assert.*;
import net.floodlightcontroller.core.module.FloodlightModuleLoader;

import org.junit.Test;

public class LoadModuleFromFileTest {

    protected void loadModulesWithoutStartUp(String fileName) throws Exception {
        // Setup logger
        System.setProperty("org.restlet.engine.loggerFacadeClass",
                "org.restlet.ext.slf4j.Slf4jLoggerFacade");

        // Load modules
        FloodlightModuleLoader fml = new FloodlightModuleLoader();
        fml.startupModules = false;
        Properties prop = new Properties();

        File f = new File(fileName);
        if (f.isFile()) {
            try {
                prop.load(new FileInputStream(fileName));
            } catch (Exception e) {
                fail(e.getMessage());
            }
        } else {
            InputStream is = getClass().getClassLoader().
                    getResourceAsStream(fileName);
            try {
                prop.load(is);
            } catch (IOException e) {
                fail(e.getMessage());
            }
        }

        String moduleList = prop.getProperty(FloodlightModuleLoader.FLOODLIGHT_MODULES_KEY)
                .replaceAll("\\s", "");
        Collection<String> configMods = new ArrayList<String>();
        configMods.addAll(Arrays.asList(moduleList.split(",")));
        fml.loadModulesFromList(configMods, prop);

    }

    @Test
    public void loadFloodlightDefaultModules() throws Exception {
        this.loadModulesWithoutStartUp("floodlightdefault.properties");
    }
}
