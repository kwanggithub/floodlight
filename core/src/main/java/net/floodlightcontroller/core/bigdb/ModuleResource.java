package net.floodlightcontroller.core.bigdb;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import net.bigdb.BigDBException;
import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBQuery;
import net.floodlightcontroller.bigdb.FloodlightResource;
import net.floodlightcontroller.core.module.FloodlightModuleLoader;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

public class ModuleResource extends FloodlightResource {

    private final FloodlightModuleLoader moduleLoader;

    public ModuleResource(FloodlightModuleLoader moduleLoader) {
        this.moduleLoader = moduleLoader;
    }

    @SuppressFBWarnings(value="EQ_COMPARETO_USE_OBJECT_EQUALS")
    // Note: this class has a natural ordering that is inconsistent with equals.
    public static class ModuleInfo implements Comparable<ModuleInfo> {
        String moduleName;
        boolean loaded;
        Set<String> dependencies;
        Set<String> services;

        @BigDBProperty(value="module-name")
        public String getModuleName() {
            return moduleName;
        }
        public void setModuleName(String moduleName) {
            this.moduleName = moduleName;
        }
        public boolean getLoaded() {
            return loaded;
        }
        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }
        public Set<String> getDependencies() {
            return dependencies;
        }
        public void setDependencies(Set<String> dependencies) {
            this.dependencies = dependencies;
        }
        public Set<String> getServices() {
            return services;
        }
        public void setServices(Set<String> services) {
            this.services = services;
        }
        @Override
        public int compareTo(ModuleInfo o) {
            return moduleName.compareTo(o.moduleName);
        }
    }

    /**
     * Retrieves all modules and their dependencies available to Floodlight.
     * @return Information about modules available or loaded.
     */
    @BigDBQuery
    public List<ModuleInfo> getAllModules() throws BigDBException {
        List<ModuleInfo> moduleList = new ArrayList<ModuleInfo>();
        Set<String> loadedModules = new HashSet<String>();

        for (IFloodlightModule m : moduleLoader.getLoadedModuleSet()) {
            loadedModules.add(m.getClass().getCanonicalName());
        }

        for (String moduleName : moduleLoader.getModuleNameMap().keySet() ) {
            ModuleInfo moduleInfo = new ModuleInfo();

            IFloodlightModule module =
                    moduleLoader.getModuleNameMap().get(
                                moduleName);

            moduleInfo.setModuleName(module.getClass().getCanonicalName());

            Collection<Class<? extends IFloodlightService>> deps =
                    module.getModuleDependencies();
            if (deps == null)
                deps = new HashSet<Class<? extends IFloodlightService>>();
            Map<String,Object> depsMap = new HashMap<String, Object> ();
            for (Class<? extends IFloodlightService> service : deps) {
                Object serviceImpl = getModuleContext().getServiceImpl(service);
                if (serviceImpl != null)
                    depsMap.put(service.getCanonicalName(), serviceImpl.getClass().getCanonicalName());
                else
                    depsMap.put(service.getCanonicalName(), "<unresolved>");

            }
            moduleInfo.setDependencies(depsMap.keySet());
            
            Collection<Class<? extends IFloodlightService>> provides = 
                    module.getModuleServices();
            if (provides == null)
                provides = new HashSet<Class<? extends IFloodlightService>>();
            Map<String,Object> providesMap = new HashMap<String,Object>();
            for (Class<? extends IFloodlightService> service : provides) {
                providesMap.put(service.getCanonicalName(), module.getServiceImpls().get(service).getClass().getCanonicalName());
            }
            moduleInfo.setServices(providesMap.keySet());

            boolean loaded = false;
            // check if this module is loaded directly
            if (loadedModules.contains(module.getClass().getCanonicalName())) {
                loaded = true;          
            } else {
                // if not, then maybe one of the services it exports is loaded
                for (Class<? extends IFloodlightService> service : provides) {
                    String modString = module.getServiceImpls().get(service).getClass().getCanonicalName();
                    if (loadedModules.contains(modString))
                        loaded = true;
                }
            }
            moduleInfo.setLoaded(loaded);
            
            moduleList.add(moduleInfo);
        }            
        Collections.sort(moduleList);
        
        return moduleList;
    }
}
