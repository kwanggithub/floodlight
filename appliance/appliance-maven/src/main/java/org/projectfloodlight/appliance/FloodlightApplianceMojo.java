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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import com.google.common.io.CharStreams;
import com.google.common.io.Files;

/**
 * Build a floodlight application appliance VM
 */
@Mojo(name="appliance", 
      requiresDependencyResolution=ResolutionScope.COMPILE,
      defaultPhase=LifecyclePhase.PACKAGE)
public class FloodlightApplianceMojo extends AbstractMojo
{
    static final String FILE_MANIFEST = "build.manifest";
    static final String SCRIPT_MANIFEST = "scripts.manifest";

    /**
     * Location of the file.
     */
    @Parameter(defaultValue="${project.build.directory}/appliance")
    private File outputDirectory;

    /**
     * The architecture to build, either i386 or amd64
     */
    @Parameter(property="appliance.arch", defaultValue="amd64")
    private String arch;

    /**
     * The suite to build, such as "precise"
     */
    @Parameter(property="appliance.suite", required=true)
    private String suite;

    /**
     * The flavor of the appliance to build
     */
    @Parameter(property="appliance.flavor", required=true)
    private String flavor;
    
    /**
     * The release string for the appliance
     */
    @Parameter(property="appliance.release", 
               defaultValue="Floodlight Appliance ${project.version}")
    private String release;
    
    /**
     * Whether to build the upgrade package after creating the VM
     */
    @Parameter(property="appliance.buildUpgrade", defaultValue="false")
    private boolean buildUpgradePackage;
    
    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Component
    protected MavenProject project;

    @Component
    protected MavenProjectHelper projectHelper;
    
    // *******
    // Setters
    // *******

    public void setArch(String arch) {
        this.arch = arch;
    }

    public void setSuite(String suite) {
        this.suite = suite;
    }

    public void setFlavor(String flavor) {
        this.flavor = flavor;
    }

    public void setRelease(String release) {
        this.release = release;
    }

    // ****
    // Mojo
    // ****

    public void execute() throws MojoExecutionException {
        getLog().info("Building appliance: " + flavor + 
                      " " + suite + "-" + arch);
        File buildDir = createBuildDir();
        createLocalAptRepo(buildDir);
        buildVM(buildDir);
        
        File qcow2 = new File(outputDirectory, 
                           flavor + "/controller-" + flavor + 
                           "-" + project.getVersion() + ".qcow2");
        getLog().info("Attaching " + qcow2 + " artifact to project");
        projectHelper.attachArtifact(project, "qcow2", null, qcow2);
        
        if (buildUpgradePackage) {
            File pkg = new File(outputDirectory, 
                                flavor + "/controller-upgrade-" + flavor + 
                                "-" + project.getVersion() + ".pkg");
            getLog().info("Attaching " + pkg + " artifact to project");
            projectHelper.attachArtifact(project, "pkg", null, pkg);
        }
    }

    // *************
    // Local methods
    // *************
    
    /**
     * Read the given manifest file and extract the given files to the specified
     * directory
     */
    private void createBuildDir(String manifest, 
                                File buildDirectory,
                                boolean executable) 
                                        throws MojoExecutionException {
        try (InputStream is = 
                FloodlightApplianceMojo.class.getResourceAsStream(manifest);
            BufferedReader br = 
                    new BufferedReader(new InputStreamReader(is, UTF_8))) {
            String line;
            while (null != (line = br.readLine())) {
                File output = new File(buildDirectory, line);
                copyResourceToFile(line, output);
                if (executable) {
                    output.setExecutable(true);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not set up appliance " + 
                                             "build environment", e);
        }
    }

    protected void copyResourceToFile(String resource, File output) 
            throws IOException {
        output.getParentFile().mkdirs();
        try (InputStream bis = 
                FloodlightApplianceMojo.class.getResourceAsStream(resource);
             BufferedReader bbr = 
                        new BufferedReader(new InputStreamReader(bis, UTF_8));
             BufferedWriter fw = Files.newWriter(output, UTF_8)) {
            String bline;
            while (null != (bline = bbr.readLine())) {
                fw.write(bline);
                fw.write('\n');
            }
        }
    }
    
    protected void recursiveDelete(File file) {
        File[] subs = file.listFiles();
        if (subs != null) {
            for (File sub : subs) {
                recursiveDelete(sub);
            }
        }
        file.delete();
    }

    /**
     * Create the build environment needed for the appliance build
     * @throws MojoExecutionException
     */
    protected File createBuildDir() throws MojoExecutionException {
        File buildDirectory = new File(outputDirectory, "build");
        if (buildDirectory.exists())
            recursiveDelete(buildDirectory);
        buildDirectory.mkdirs();

        createBuildDir(FILE_MANIFEST, buildDirectory, false);
        createBuildDir(SCRIPT_MANIFEST, buildDirectory, true);
        return buildDirectory;
    }
    
    /**
     * Create a local apt repository containing the packages for the local
     * build
     * @throws MojoExecutionException
     */
    protected void createLocalAptRepo(File buildDirectory) 
            throws MojoExecutionException {
        File repoDirectory = new File(outputDirectory, "debian");
        recursiveDelete(repoDirectory);
        repoDirectory.mkdirs();
        try {
            copyResourceToFile("distributions", 
                               new File(repoDirectory, "conf/distributions"));
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to configure apt repo", e);
        }
        
        addArtifacts(new HashSet<Artifact>(project.getAttachedArtifacts()), repoDirectory);
        addArtifacts(project.getArtifacts(), repoDirectory);        
    }
    
    protected void addArtifacts(Set<Artifact> artifacts, File repoDirectory)
            throws MojoExecutionException {
        for (Artifact artifact : artifacts) {
            if ("deb".equals(artifact.getType())) {
                getLog().info("Adding " + artifact + 
                              " to local apt repository");
                reprepro(repoDirectory, artifact.getFile());
            } 
        }
    }
    
    /**
     * Install a debian package into a reprepro archive
     * @param repoDir
     * @param deb
     * @throws MojoExecutionException
     */
    protected void reprepro(File repoDir, File deb) 
            throws MojoExecutionException {
        try {
            ProcessBuilder pb = 
                    new ProcessBuilder("reprepro",
                                       "-b", repoDir.getAbsolutePath(),
                                       "includedeb", 
                                       suite, deb.getAbsolutePath());
            Process p = pb.start();
            pb.redirectErrorStream(true);
            String out = CharStreams.
                    toString(new InputStreamReader(p.getInputStream(), 
                                                   StandardCharsets.UTF_8));
            
            int status = p.waitFor();
            if (status != 0) {
                throw new MojoExecutionException("Error running reprepro: " +
                        status + "\nOutput:\n" + out);
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run reprepro", e);
        }
    }
 
    /**
     * Build the actual VM by calling the build-vm script
     * @throws MojoExecutionException
     */
    protected void buildVM(File buildDir) throws MojoExecutionException {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "build-vm.sh");
            pb.directory(buildDir);
            pb.environment().put("ARCH", arch);
            pb.environment().put("SUITE", suite);
            pb.environment().put("FLAVOR", flavor);
            pb.environment().put("RELEASE", release);
            pb.environment().put("TARGET_DIR", 
                                 outputDirectory.getAbsolutePath());
            pb.environment().put("VERSION", project.getVersion());
            if (buildUpgradePackage) {
                pb.environment().put("BUILD_UPGRADE", "true");
            }
            pb.redirectErrorStream(true);
            getLog().info("Starting vm-build.sh");
            Process p = pb.start();
            InputStreamReader isr = 
                    new InputStreamReader(p.getInputStream(), 
                                          StandardCharsets.UTF_8);
            
            try (BufferedReader br = new BufferedReader(isr)) {
                String line;
                while (null != (line = br.readLine())) {
                    getLog().info(line);
                }
            }
            int status = p.waitFor();
            if (status != 0)
                throw new MojoExecutionException("build-vm exited with " + 
                                                 "status " + status);
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to run build-vm.sh", e);
        }
    }
}
    