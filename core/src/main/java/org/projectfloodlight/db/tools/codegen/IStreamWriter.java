package org.projectfloodlight.db.tools.codegen;

import org.projectfloodlight.db.BigDBException;


/**
 * A simple text writer that can continue with multiple
 * units, e.g., files.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */
public interface IStreamWriter {
    
    public void createWriter(String name) throws BigDBException;
    
    public void closeWriter() throws BigDBException;
}
