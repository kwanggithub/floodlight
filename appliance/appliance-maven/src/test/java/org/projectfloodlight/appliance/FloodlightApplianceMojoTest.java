/*
 * Copyright 2013 Big Switch Networks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.projectfloodlight.appliance;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.projectfloodlight.appliance.FloodlightApplianceMojo;

public class FloodlightApplianceMojoTest {

    FloodlightApplianceMojo fam;
    
    @Rule
    public TemporaryFolder basePath = new TemporaryFolder();
    
    @Before
    public void setup() throws Exception {
        fam = new FloodlightApplianceMojo();
        fam.setArch("amd64");
        fam.setFlavor("precise");
        fam.setOutputDirectory(basePath.getRoot());
        fam.setRelease("Test Release");
        fam.setFlavor("test");
    }
    
    @Test
    public void testCreateBuildDir() throws Exception {
        fam.createBuildDir();
        assertTrue((new File(basePath.getRoot(), "build/")).exists());
        assertTrue((new File(basePath.getRoot(), 
                             "build/build-vm.sh")).canExecute());
        assertTrue((new File(basePath.getRoot(), 
                             "build/templates/ubuntu/fstab.tmpl")).exists());
    }
    
}
