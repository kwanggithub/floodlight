package net.bigdb.tools.codegen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import net.bigdb.BigDBException;

import com.google.common.io.Files;

/**
 * A simple text writer that can continue with multiple
 * units, e.g., files.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */
public class TextWriter implements IStreamWriter {
    
    private static final int TAB_SPACE_COUNT = 4;
    
    protected String path;
    
    protected BufferedWriter writer;
        
    public TextWriter(String path) {
        this.path = path;
    }

    protected String toUpperFisrt(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
    
    public void emitTabs(int number, BufferedWriter out) 
            throws BigDBException {
        try {
            if (number <= 0) 
                return;
            for (int i = 0; i < number * TAB_SPACE_COUNT; ++i) {
                out.append(" ");
            }
            out.flush();
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }
    
    public void emitLineEnd(BufferedWriter out) 
            throws BigDBException {
        try {
            out.newLine();
            out.flush();
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }

    public void emitLine(int numTabs, String line, BufferedWriter out) 
            throws BigDBException {
        try {
            this.emitTabs(numTabs, out);
            out.write(line == null ? "" : line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }

    public void emitLineFirstPart(int numTabs, String line, BufferedWriter out) 
            throws BigDBException {
        try {
            this.emitTabs(numTabs, out);
            out.write(line);
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }
    
    public void emitRestOfLine(String line, BufferedWriter out) 
            throws BigDBException {
        try {
            out.write(line);
            out.newLine();
            out.flush();
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }

    public void createWriter(String name) throws BigDBException {
        try {
            writer = Files.newWriter(new File(path, name), 
                                     StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }
    
    public void closeWriter() throws BigDBException {
        try {
            writer.close();
            writer = null;
        } catch (IOException e) {
            throw new BigDBException(e.getMessage());
        }
    }
}
