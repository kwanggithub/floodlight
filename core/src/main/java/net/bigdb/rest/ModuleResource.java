package net.bigdb.rest;

import java.util.Map;
import java.util.HashMap;

import net.bigdb.BigDBException;
import net.bigdb.schema.Module;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;

import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleResource extends BigDBResource {

    protected final static Logger logger =
            LoggerFactory.getLogger(ModuleResource.class);
    
    private void addModuleInfo(Module module, Map<String,Object> map)
            throws BigDBException {
        map.put("typedefs", module.getTypedefs());
    }
    
    @Get("json")
    public Representation getJsonSchema() throws BigDBException {
        String treespaceName = "";
        String moduleName = "";
        try {
            Service service = (Service) getContext().getAttributes().get(
                                                                         BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
            treespaceName = (String) getRequestAttributes().get("treespace");
            moduleName = (String) getRequestAttributes().get("name");
            if (moduleName != null && moduleName.isEmpty())
                moduleName = null;
            String moduleRevision = (String) getRequestAttributes().get("revision");
            if (moduleRevision != null && moduleRevision.isEmpty())
                moduleRevision = null;
            Treespace treespace = service.getTreespace(treespaceName);
            Map<ModuleIdentifier,Module> modules =
                    treespace.getSchema().getModules();
            Map<String,Object> rootMap = new HashMap<String,Object>();
            if (moduleName != null) {
                ModuleIdentifier moduleId =
                        new ModuleIdentifier(moduleName, moduleRevision);
                Module module = modules.get(moduleId);
                addModuleInfo(module, rootMap);
            } else {
                for (Map.Entry<ModuleIdentifier,Module> entry: modules.entrySet()) {
                    Map<String,Object> revisionMap = null;
                    String nextModuleName = entry.getKey().getName();
                    String nextModuleRevision = entry.getKey().getRevision();
                    if (nextModuleRevision == null)
                        nextModuleRevision = "";
                    // FIXME: Get rid of this unchecked warning
                    @SuppressWarnings("unchecked")
                    Map<String,Object> moduleMap = (Map<String,Object>)
                            rootMap.get(nextModuleName);
                    if (moduleMap == null) {
                        moduleMap = new HashMap<String,Object>();
                        rootMap.put(nextModuleName, moduleMap);
                    }
                    revisionMap = new HashMap<String,Object>();
                    addModuleInfo(entry.getValue(), revisionMap);
                    moduleMap.put(nextModuleRevision, revisionMap);
                }
            }
            JacksonRepresentation<Map<String,Object>> representation =
                    new JacksonRepresentation<Map<String,Object>>(rootMap);
            representation.setObjectMapper(mapper);
            return representation;
        } catch (Exception e) {
            String message = "Failed to query module in treespace " + treespaceName + 
                             " for module " + moduleName;
            logger.error(message + (e.getMessage() == null ? "" : 
                                    " with error " + e.getMessage()));
            throw new BigDBException(message, e);
        }
    }
}
