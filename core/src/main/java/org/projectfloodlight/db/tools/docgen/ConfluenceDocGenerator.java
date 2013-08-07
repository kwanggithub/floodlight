package org.projectfloodlight.db.tools.docgen;

import org.projectfloodlight.db.BigDBException;

/**
 * A Schema tree visitor to emit document.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */
public class ConfluenceDocGenerator extends DocGenerator {
    
    public ConfluenceDocGenerator(String sampleFile, String path) 
            throws BigDBException {
        super(sampleFile, path, new ConfluenceWikiWriter(path));
    }
}
