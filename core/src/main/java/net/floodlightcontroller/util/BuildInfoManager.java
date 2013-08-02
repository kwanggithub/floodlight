package net.floodlightcontroller.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.joda.time.DateTime;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * BuildInfoManager. Scans the MANIFEST files in the class path for sections
 * titled 'Floodlight-buildinfo" and collections the information in versionInfo
 * objects that can be queried, e.g., for logging. The section looks like this.
 * <code>
 *  Name: Floodlight-buildinfo
 *  projectName: Floodlight
 *  version: 0.8
 *  vcsRevision: 1303737
 *  vcsBranch: bigdb
 *  vcsDirty: true
 *  buildUser: andi
 *  buildDate: 2013-04-05T11:49:19-0700
 *  </code>
 **/
public class BuildInfoManager {
    private final static Logger logger = LoggerFactory.getLogger(BuildInfoManager.class);

    private final HashMap<String, BuildInfo> buildInfos;
    private final Set<String> loadedUris;

    private static class SingletonHolder {
        public static final BuildInfoManager INSTANCE = new BuildInfoManager();
    }

    /** return the singleton instance of BuildInfoManager */
    public static BuildInfoManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private BuildInfoManager() {
        buildInfos = new LinkedHashMap<String, BuildInfo>();
        loadedUris = new HashSet<String>();
    }

    /**
     * return an <emph>Optional</emph> on the buildinfo.
     *
     * @see Optional
     **/
    public synchronized Optional<BuildInfo> getBuildInfo(String projectName) {
        BuildInfo info = buildInfos.get(projectName);
        if (info == null) {
            loadAllBuildInfosSafely();
        }
        return Optional.fromNullable(info);
    }

    /** discover all buildinfos and return an Iterable */
    public synchronized Iterable<BuildInfo> getAllBuildInfos() {
        loadAllBuildInfosSafely();
        return buildInfos.values();
    }

    /**
     * wrapper method that catches and logs any exceptions. BuildInfos are a
     * best-effort business and should never result in floodlight shutting down.
     */
    private synchronized void loadAllBuildInfosSafely() {
        try {
            loadAllBuildInfos();
        } catch (Exception e) {
            logger.warn("Error trying to load build infos", e);
        }
    }

    private synchronized void loadAllBuildInfos() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        Enumeration<URL> resources;
        try {
            resources = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
        } catch (IOException e) {
            logger.warn("Error reading manifest collection ", e);
            return;
        }

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            if (loadedUris.contains(url.toString()))
                continue;

            try (InputStream stream = url.openStream()) {
                Manifest manifest = new Manifest(stream);

                Attributes attrs = manifest.getAttributes("Floodlight-buildinfo");
                if (attrs == null)
                    continue;

                String projectName = attrs.getValue("projectName");
                if (!Strings.isNullOrEmpty(projectName)) {
                    String version = attrs.getValue("version");
                    String vcsRevision = attrs.getValue("vcsRevision");
                    String vcsBranch = attrs.getValue("vcsBranch");
                    boolean vcsDirty = Boolean.parseBoolean(attrs.getValue("vcsDirty"));
                    String buildUser = attrs.getValue("buildUser");

                    DateTime buildDate;
                    String dateString = attrs.getValue("buildDate");
                    try {
                        buildDate = new DateTime(dateFormat.parse(dateString));
                    } catch (ParseException e) {
                        logger.warn("Could not parse date: " + dateString);
                        buildDate = null;
                    }

                    String jarFile = getJarFromURL(url);
                    BuildInfo info =
                            new BuildInfo(projectName, jarFile, version, buildDate, buildUser,
                                          vcsRevision, vcsBranch, vcsDirty);
                    buildInfos.put(info.getProjectName(), info);
                }
                loadedUris.add(url.toString());
            } catch (IOException e) {
                logger.warn("Error reading manifest file from ", e);
            }
        }
    }

    private String getJarFromURL(URL url) {
        String text = url.toString();
        int idx = text.lastIndexOf('!');
        if (idx > 0) {
            text = text.substring(0, idx);
            // now lets remove all but the file name
            idx = text.lastIndexOf('/');
            if (idx > 0) {
                text = text.substring(idx + 1);
            }
            idx = text.lastIndexOf('\\');
            if (idx > 0) {
                text = text.substring(idx + 1);
            }
            return text;
        } else {
            return "";
        }
    }
}
