package net.bigdb.schema.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.schema.DuplicateTypedefException;
import net.bigdb.schema.ExtensionSchemaNode;
import net.bigdb.schema.GroupingSchemaNode;
import net.bigdb.schema.Module;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.ModulePrefixNotFoundException;
import net.bigdb.schema.TypedefSchemaNode;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ModuleImpl implements Module {

    protected ModuleIdentifier moduleId;
    protected String prefix;
    protected Map<String, TypedefSchemaNode> typedefs =
            new HashMap<String, TypedefSchemaNode>();
    protected Map<String, Module> importedModules =
            new HashMap<String, Module>();
    protected Map<String, GroupingSchemaNode> groupings = 
            new HashMap<String, GroupingSchemaNode>();
    protected Map<String, ExtensionSchemaNode> extensions = 
            new HashMap<String, ExtensionSchemaNode>();
    
    public ModuleImpl(ModuleIdentifier moduleId, String prefix) {
        this.moduleId = moduleId;
        this.prefix = prefix;
    }
    
    @Override
    public ModuleIdentifier getId() {
        return moduleId;
    }
    
    @Override
    public Map<String,TypedefSchemaNode> getTypedefs() {
        return typedefs;
    }

    @Override
    public Map<String, GroupingSchemaNode> getGrouping() {
        return this.groupings;
    }
    //@Override
    public Map<String, ExtensionSchemaNode> getExtensions() {
        return Collections.unmodifiableMap(this.extensions);
    }
    
    @JsonIgnore
    public TypedefSchemaNode getTypedef(String prefix, String name)
            throws BigDBException {
        TypedefSchemaNode typedef = null;
        if ((prefix == null) || prefix.equals(this.prefix)) {
            typedef = typedefs.get(name);
        } else {
            Module module = importedModules.get(prefix);
            if (module == null)
                throw new ModulePrefixNotFoundException(prefix);
            typedef = module.getTypedefs().get(name);
        }
        return typedef;
    }
    
    @JsonIgnore
    public Module getImportedModule(String prefix) {
        assert prefix != null;
        return importedModules.get(prefix);
    }
    
    public String getPrefix() {
        return this.prefix;
    }
 
    @JsonIgnore
    public GroupingSchemaNode getGrouping(String prefix, String name)
            throws BigDBException {
        GroupingSchemaNode groupingNode = null;
        if ((prefix == null) || prefix.equals(this.prefix)) {
            groupingNode = groupings.get(name);
        } else {
            Module module = importedModules.get(prefix);
            if (module == null)
                throw new ModulePrefixNotFoundException(prefix);
            groupingNode = module.getGrouping().get(name);
        }
        return groupingNode;
    }
    
    public void addTypedef(TypedefSchemaNode typedef) {
        assert typedef != null;
        this.typedefs.put(typedef.getName(), typedef);
    }
    
    public void addGrouping(GroupingSchemaNode groupingNode) {
        this.groupings.put(groupingNode.getName(), groupingNode);
    }
    
    public void addImportedModule(String prefix, ModuleImpl module) {
        assert prefix != null;
        assert module != null;
        importedModules.put(prefix, module);
    }
    
    public void includeSubmodule(ModuleImpl submodule) throws BigDBException {
        for (String typedefName: submodule.typedefs.keySet()) {
            if (typedefs.containsKey(typedefName)) {
                throw new DuplicateTypedefException(typedefName);
            }
        }
        typedefs.putAll(submodule.typedefs);
        this.groupings.putAll(submodule.groupings);
    }
    
    public void addExtension(ExtensionSchemaNode extension) {
        this.extensions.put(extension.getName(), extension);
    }
    
    public ExtensionSchemaNode getExtension(String name) {
        return this.extensions.get(name);
    }
}
