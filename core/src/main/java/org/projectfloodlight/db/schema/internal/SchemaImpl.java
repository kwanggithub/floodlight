package org.projectfloodlight.db.schema.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.schema.AggregateSchemaNode;
import org.projectfloodlight.db.schema.ContainerSchemaNode;
import org.projectfloodlight.db.schema.InvalidSchemaTypeException;
import org.projectfloodlight.db.schema.Module;
import org.projectfloodlight.db.schema.ModuleIdentifier;
import org.projectfloodlight.db.schema.ModuleNotFoundException;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.schema.SchemaNodeNotFoundException;
import org.projectfloodlight.db.schema.SchemaNode.NodeType;
import org.projectfloodlight.db.util.FileLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class SchemaImpl implements Schema {
    protected static Logger logger = 
            LoggerFactory.getLogger(FileLocator.class);
    
    // The collection of search paths to use to find modules
    protected FileLocator moduleLocator;
    
    // The list of all of the modules that are loaded in the schema
    protected Map<ModuleIdentifier, Module> modules;
    
    protected List<ModuleLoader> moduleLoaders;
    
    // The root of the schema tree, which contains all of the schema nodes
    // aggregated across all of the loaded modules.
    protected AggregateSchemaNode schemaRoot;
    
    public SchemaImpl() {
        this.modules = new HashMap<ModuleIdentifier, Module>();
        this.moduleLocator = new FileLocator();
        this.moduleLocator.addSearchResource("schemas.manifest");

        this.moduleLoaders = new ArrayList<ModuleLoader>();
        // Currently only the YANG module loader is supported so we just
        // add it here. Potentially could make this more configurable in the
        // future if/when we support other module formats/loaders.
        this.moduleLoaders.add(new YangModuleLoader());
        this.schemaRoot = new ContainerSchemaNode("", null);
        this.schemaRoot.setAttribute("Config", "true");
    }
    
    // FIXME: This is a short-term hack for some of the unit tests.
    // Should not use in other code!!!
    public SchemaImpl(ContainerSchemaNode schemaRoot) {
        this.schemaRoot = schemaRoot;
    }
    
    @JsonIgnore
    public FileLocator getModuleLocator() {
        return moduleLocator;
    }
    
    @Override
    public Map<ModuleIdentifier,Module> getModules() {
        return modules;
    }
    
    @JsonIgnore
    @Override
    public Module getModule(ModuleIdentifier moduleId) {
        return modules.get(moduleId);
    }

    public void addModule(ModuleImpl module)
            throws BigDBException {
        ModuleIdentifier moduleId = module.getId();
//        if (modules.containsKey(moduleId))
//            throw new DuplicateModuleException(moduleId);
        modules.put(moduleId, module);
    }

    public ModuleImpl loadModule(ModuleIdentifier moduleId, File directory,
            boolean recursive) throws BigDBException {        
        // First check to see if we've already loaded it
        ModuleImpl module = (ModuleImpl) modules.get(moduleId);
        if (module != null)
            return module;
        
        FileLocator locator;
        if (directory != null) {
            locator = new FileLocator();
            locator.addSearchPath(directory, recursive);
        } else {
            locator = moduleLocator;
        }
        
        // We need to load it.
        for (ModuleLoader moduleLoader: moduleLoaders) {
            String moduleFileName = moduleLoader.getModuleFileName(moduleId);
            assert moduleFileName != null;

            
            try (InputStream moduleFile = locator.findFile(moduleFileName)) {
                if (moduleFile != null) {
                    module = moduleLoader.loadModule(this, moduleId, moduleFile);
                    modules.put(moduleId, module);
                    return module;
                }
            } catch (IOException e) {
                throw new BigDBException("Unable to load module", e);
            }
        }
        
        throw new ModuleNotFoundException(moduleId);
    }
    
    public ModuleImpl loadModule(ModuleIdentifier moduleId)
            throws BigDBException {
        return loadModule(moduleId, null, false);
    }
    
//    private ModuleImpl loadModuleInputStream(ModuleIdentifier moduleId,
//            InputStream inputStream) throws BigDBException {
//        YangModuleLoader yangModuleLoader = new YangModuleLoader();
//        ModuleImpl module = yangModuleLoader.loadModule(this, moduleId,
//                inputStream);
//        return module;
//    }
    
//    private ModuleImpl loadModuleFile(ModuleIdentifier moduleId,
//            File moduleFile) throws BigDBException {
//        try {
//            // Set up the YANG parser instance to read from the module file
//            InputStream inputStream = new FileInputStream(moduleFile);
//            ModuleImpl module = loadModuleInputStream(moduleId, inputStream);
//            return module;
//        }
//        catch (FileNotFoundException exc) {
//            throw new BigDBException("Schema file not found: " +
//                    moduleId.toString(), exc);
//        }
//    }

//    private void resolveTypedefs() throws BigDBException {
//        
//        TypedefResolver resolver = new TypedefResolver();
//
//        for (Module module: modules.values()) {
//            ModuleImpl moduleImpl = (ModuleImpl) module;
//            for (TypedefSchemaNode typedefSchemaNode:
//                    moduleImpl.getTypedefs().values()) {
//                typedefSchemaNode.accept(resolver);
//            }
//        }
//        
//        schemaRoot.accept(resolver);
//    }
    
    public void finishLoading() throws BigDBException {
//        resolveTypedefs();
    }
    
    public AggregateSchemaNode getSchemaRoot() {
        return schemaRoot;
    }
    
    @JsonIgnore
    @Override
    public SchemaNode getSchemaNode(LocationPathExpression path) throws BigDBException {
        
        assert path != null;
        
        SchemaNode schemaNode = schemaRoot;
        
        int stepCount = path.getSteps().size();
        for (int i = 0; i < stepCount; i++) {
            Step step = path.getStep(i);
            String name = step.getName();
            try {
                schemaNode = schemaNode.getChildSchemaNode(name);
                if ((schemaNode.getNodeType() != NodeType.LIST) &&
                        !step.getPredicates().isEmpty()) {
                    throw new InvalidSchemaTypeException(
                            "Predicates only allowed with list nodes");
                }
            } catch (SchemaNodeNotFoundException exc) {
                String pathString = path.toString();
                String details = exc.getDetails();
                throw new SchemaNodeNotFoundException(pathString, details);
            }
        }
        
        return schemaNode;
    }
    
    @JsonIgnore
    @Override
    public boolean isListQuery(Query query) throws BigDBException {
        // We currently don't support more complex queries so we don't hit
        // the case where we'd return a list.
        // FIXME: Need to change this when we get real query processing.
        return false;
    }
}
