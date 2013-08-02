package net.bigdb.tools.docgen;

import net.bigdb.BigDBException;

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
