package org.projectfloodlight.db.tools.docgen;

import java.io.BufferedWriter;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.tools.codegen.IStreamWriter;

/**
 * 
 * @author kevin.wang@bigswitch.com
 *
 */
public interface IDocFormatter extends IStreamWriter {

    public String getSkippedString(String text);
    
    public void emitSectionEnd() throws BigDBException;
    
    public void emitTableHeader(String[] headers) 
            throws BigDBException;
    
    public void emitTableRow(String[] items) 
            throws BigDBException;
    
    public void emitTableRow(String[] headers, String sep) 
            throws BigDBException;
    
    public void emitTabs(int number, BufferedWriter out) 
            throws BigDBException;
    
    public String getStringWithLink(String s, String link);
    
    public void emitHeaderWithLink(int level, int tabs, 
                                   String header, String link) 
            throws BigDBException;
    
    public void emitHeader(int level, int tabs, String header) 
            throws BigDBException;

    public void emitText(int tabs, String text, boolean endLine) 
            throws BigDBException;
    
    public void emitText(int tabs, String text) 
            throws BigDBException;
    
    public void emitCodeTextWithExpand(String name, String text)
            throws BigDBException;
}
