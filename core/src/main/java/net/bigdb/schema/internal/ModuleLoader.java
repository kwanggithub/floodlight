package net.bigdb.schema.internal;

import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.schema.ModuleIdentifier;

/**
 * Interface for loading module/schema data in different formats, e.g. yang
 * @author rob.vaterlaus@bigswitch.com
 */
public interface ModuleLoader {
    /**
     * Construct the name of the module file
     * @param moduleId module identifier (module name + revision)
     * @return name of the file
     */
    public String getModuleFileName(ModuleIdentifier moduleId);
    
    /**
     * Load the module from the given input stream
     * @param schema
     * @param moduleId
     * @param inputStream
     * @return the loaded module
     * @throws BigDBException
     */
    public ModuleImpl loadModule(SchemaImpl schema,
            ModuleIdentifier moduleId, InputStream inputStream)
            throws BigDBException;
}
