package org.projectfloodlight.util;

import org.joda.time.DateTime;

/** Build information about a build artefact (jar) in the floodlight system. This information
 *  is gathered from the section Floodlight-buildinfo in the apropriate jar files.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BuildInfo {
    /** whether buildinfo was found. All other attributes are invalid iff found==false */
    private final boolean found;

    /** the project name. set by at to ant.projectName */
    private final String projectName;
    /** name of the jar file this buildinfo concerns */
    private final String jarFile;
    /** symbolic project version */
    private final String version;
    /** project build date */
    private final DateTime buildDate;
    /** user that build project */
    private final String buildUser;
    /** vcs (git) revision on which the project was build */
    private final String vcsRevision;
    /** vcs (git) branch */
    private final String vcsBranch;
    /** whether the vcs (git) source tree had local changes during build */
    private final boolean vcsDirty;

    BuildInfo(String projectName) {
        this.found = false;
        this.projectName = projectName;
        this.version = null;
        this.buildDate = null;
        this.buildUser = null;
        this.vcsRevision = null;
        this.vcsBranch = null;
        this.vcsDirty = false;
        this.jarFile = null;
    }

    BuildInfo(String projectName, String jarFile, String version, DateTime buildDate, String buildUser,
            String vcsRevision, String vcsBranch, boolean vcsDirty) {
        this.jarFile = jarFile;
        this.found = true;
        this.projectName = projectName;
        this.version = version;
        this.buildDate = buildDate;
        this.buildUser = buildUser;
        this.vcsRevision = vcsRevision;
        this.vcsBranch = vcsBranch;
        this.vcsDirty = vcsDirty;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getVersion() {
        return version;
    }

    public DateTime getBuildDate() {
        return buildDate;
    }

    public String getBuildUser() {
        return buildUser;
    }

    public String getVcsRevision() {
        return vcsRevision;
    }

    public String getVcsBranch() {
        return vcsBranch;
    }

    public boolean isVcsDirty() {
        return vcsDirty;
    }

    public boolean isFound() {
        return found;
    }

    public String getJarFile() {
        return jarFile;
    }

    /** delegates to toString(true, true) */
    @Override
    public String toString() {
        return toString(true,true);
    }

    /** return a textual representation of the buildinfo.
     *
     * @param buildInfo whether to append information about the build (time, user)
     * @param vcsInfo whether ot append information about the vcs source (git revision etc.)
     * @return
     */
    public String toString(boolean buildInfo, boolean vcsInfo) {
        StringBuilder res = new StringBuilder();
        res.append(projectName);
        res.append(':');

        if (found) {
            if(jarFile != null)
                res.append(' ').append(jarFile);

            if (version != null) {
                res.append(" Version ").append(version);
            }
            if (buildInfo) {
                res.append(". Built by ").append(buildUser).append(" on ").append(buildDate);
            }
            if (vcsInfo) {
                res.append(". VCS Branch ").append(this.vcsBranch).append(" Revision ")
                        .append(this.vcsRevision);
                if(vcsDirty) {
                    res.append(" [local changes]");
                }
            }
        } else {
            res. append(" (not found) ");
        }
        return res.toString();
    }


}
