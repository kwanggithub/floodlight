package org.projectfloodlight.db.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Locate a file by name in a configured set of search paths.
 * A search path can be configured to search recursively in its subdirectories.
 * 
 * @author rob.vaterlaus@bigswitch.com
 */
public class FileLocator {
    protected static final Logger logger = 
            LoggerFactory.getLogger(FileLocator.class);
    
    private static class SearchPath {

        public File directory;
        public boolean recursive;
        
        public SearchPath(File directory, boolean recursive) {
            this.directory = directory;
            this.recursive = recursive;
        }
    }
    private static class SearchResource {
        public String manifestPath;

        public SearchResource(String manifestPath) {
            super();
            this.manifestPath = manifestPath;
        }
    }

    private final List<SearchPath> searchPaths;
    private final List<SearchResource> searchResources;

    public FileLocator() {
        searchPaths = new ArrayList<SearchPath>();
        searchResources = new ArrayList<SearchResource>();
    }
    
    /**
     * Add a search path to look in to find a file.
     * @param directory the directory to search in
     * @param recursive search recursively in the subdirectories
     */
    public void addSearchPath(File directory, boolean recursive) {
        searchPaths.add(new SearchPath(directory, recursive));
    }

    public void addSearchResource(String manifestPath) {
        searchResources.add(new SearchResource(manifestPath));
    }
    
    /**
     * Find a file with the specified name in the specified directory,
     * optionally searching recursively.
     * 
     * @param name name of the file to search for
     * @param directory directory to search in
     * @param recursive search recursively in the subdirectories
     * @return
     */
    private InputStream findFile(String name, 
                                     File directory, 
                                     boolean recursive) {
        
        assert name != null;
        assert directory != null;
        
        InputStream foundFile = null;
        File[] files = directory.listFiles();
        if (files == null)
            return null;
        
        List<File> subdirectories = new ArrayList<File>();
        
        // See if the requested file exists in this directory
        for (File file : files) {
            if (name.equals(file.getName())) {
                try {
                    return new FileInputStream(file);
                } catch (FileNotFoundException e) {
                    logger.debug("Could not open {} for reading", file);
                }
            }
            if (file.isDirectory() && recursive)
                subdirectories.add(file);
        }
        Collections.sort(subdirectories);
        
        // If we didn't find it in the top-level directory,
        // search through the subdirectories we discovered above.
        for (File subdirectory : subdirectories) {
            foundFile = findFile(name, subdirectory, true);
            if (foundFile != null)
                return foundFile;
        }

        return null;
    }

    /**
     * Find the file with the specified name, searching in the configured
     * search paths.
     * @param name
     * @return the found file or null if it was not found
     */
    @SuppressFBWarnings(value="DM_DEFAULT_ENCODING")
    public InputStream findFile(String name) {
        ClassLoader cl = getClass().getClassLoader();
        for (SearchResource sr : searchResources) {
            try {
                Enumeration<URL> resources = cl.getResources(sr.manifestPath);
                
                while (resources.hasMoreElements()) {
                    URL manifest = resources.nextElement();
                    InputStream is = manifest.openStream();
                    if (is == null) continue;
                    try (BufferedReader br = 
                            new BufferedReader(new InputStreamReader(is))) {
                        String line;
                        while (null != (line = br.readLine())) {
                            if (line.endsWith(name)) {
                                logger.trace("Found module {} in resource {} " + 
                                             "from manifest {}", 
                                             name, line, manifest);
                                return cl.getResourceAsStream(line);
                            }
                        }
                    } 
                }
            } catch (IOException e) {
                logger.debug("Failed to load resource manifest", e);
            }
        }
        for (SearchPath searchPath: searchPaths) {
            InputStream file = findFile(name, searchPath.directory,
                    searchPath.recursive);
            if (file != null) {
                logger.trace("Found module {} in directory {}", 
                             name, searchPath.directory);
                return file;
            }
        }
        return null;
    }
}
