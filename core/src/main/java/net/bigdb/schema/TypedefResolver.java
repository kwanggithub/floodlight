package net.bigdb.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.schema.internal.ModuleImpl;

public class TypedefResolver extends AbstractSchemaNodeVisitor {
    
    private Map<ModuleIdentifier, Module> modules;
    
    public TypedefResolver(Map<ModuleIdentifier, Module> modules) {
        this.modules = modules;
    }
    
    private TypeSchemaNode resolveBaseTypedef(String baseTypePrefix, 
                                              String baseTypeName,
                                              ModuleIdentifier moduleId)
            throws BigDBException {
        ModuleImpl module = (ModuleImpl) modules.get(moduleId);
        if (module == null)
            throw new ModuleNotFoundException(moduleId);
        // FIXME: is it correct that use the prefix for the same
        // containing module?
        Module sourceModule = (baseTypePrefix != null) ?
                    module.getImportedModule(baseTypePrefix) : module;
        if (sourceModule == null)
            throw new ModulePrefixNotFoundException(baseTypePrefix);
        TypedefSchemaNode typedefSchemaNode =
               sourceModule.getTypedefs().get(baseTypeName);
        if (typedefSchemaNode == null)
            throw new TypedefNotFoundException(baseTypeName);
        // recursively resolve derived types
        if (typedefSchemaNode.getLeafType() == SchemaNode.LeafType.NEED_RESOLVE) {
             resolveBaseTypedef(typedefSchemaNode);
        } 

        assert typedefSchemaNode.getLeafType() != 
                SchemaNode.LeafType.NEED_RESOLVE;
        return typedefSchemaNode.getBaseTypedef();
    }

    private void resolveBaseTypedef(ScalarSchemaNode scalarSchemaNode)
            throws BigDBException {
        // type information is save in baseTypedef.
        TypeSchemaNode baseType = scalarSchemaNode.getBaseTypedef();
        String baseTypeName = baseType.getBaseTypeName();
        if (baseTypeName == null && 
            baseType.getLeafType() == SchemaNode.LeafType.NEED_RESOLVE) {
            throw new BigDBException("Using derived type without "
                                     + "a name for node: "
                                     + scalarSchemaNode.getName());
        }
        if (baseType.getLeafType() == SchemaNode.LeafType.NEED_RESOLVE) {
            ModuleIdentifier moduleId = scalarSchemaNode.getModule();
            String baseTypePrefix = baseType.getBaseTypePrefix();
            TypeSchemaNode tn = resolveBaseTypedef(baseTypePrefix, 
                                                   baseTypeName, 
                                                   moduleId);
            scalarSchemaNode.resolveType(tn);
        } else if (baseType.getLeafType() == SchemaNode.LeafType.UNION) {
            // we need to resolve the types in union
            visit(baseType);
        }
        assert scalarSchemaNode.getLeafType() != SchemaNode.LeafType.NEED_RESOLVE;
    }
    
    @Override
    public Result visit(LeafSchemaNode leafSchemaNode) throws BigDBException {
        resolveBaseTypedef(leafSchemaNode);

        // FIXME: The following code is a kludgy fix for the problem that if
        // the type of a leaf node is one of the built-in types and it defines
        // a default value the defaultValueString string is never resolved to
        // the defaultValue data node. That's because the leaf type of the
        // leaf node is not NEED_RESOLVE so it never hits the code that
        // sets defaultValue. A better way to fix I think this would be to
        // change the code to decouple the resolution of the typedefs from the
        // resolution of the default value, but there didn't seem to be a
        // simple way to change the code to do that and I didn't want to make
        // significant changes to the schema/type loading/resolution logic at
        // this point in the release cycle. We should revisit this code after
        // the release.
        TypeSchemaNode baseType = leafSchemaNode.getBaseTypedef();
        String defaultValueString = baseType.getDefaultValueString();
        DataNode defaultValue = baseType.getDefaultValue();
        if ((defaultValue == null) && (defaultValueString != null)) {
            defaultValue = baseType.parseDataValueString(defaultValueString);
            leafSchemaNode.setDefaultValue(defaultValue);
        }

        return Result.CONTINUE;
    }

    @Override
    public Result visit(LeafListSchemaNode leafListSchemaNode) throws BigDBException {
        // For leaf List node, the type is in leafNode.
        resolveBaseTypedef(leafListSchemaNode.getLeafSchemaNode());
//        leafListSchemaNode.setLeafType(leafListSchemaNode.getLeafSchemaNode().getLeafType());
        return Result.CONTINUE;
    }
    
    @Override
    public Result visit(TypedefSchemaNode typedefSchemaNode)
            throws BigDBException {
        resolveBaseTypedef(typedefSchemaNode);
        return Result.CONTINUE;
    }

    @Override
    public Result visit(TypeSchemaNode typeSchemaNode)
            throws BigDBException {
        if (typeSchemaNode.getLeafType() == SchemaNode.LeafType.UNION &&
            typeSchemaNode instanceof UnionTypeSchemaNode) {
            UnionTypeSchemaNode ut = (UnionTypeSchemaNode)typeSchemaNode;
            List<TypeSchemaNode> tns = ut.getTypeSchemaNodes();
            List<TypeSchemaNode> newTypes = new ArrayList<TypeSchemaNode>();
            for (TypeSchemaNode sn : tns) {
                if (sn.getLeafType() == SchemaNode.LeafType.NEED_RESOLVE) {
                    String prefix = sn.getBaseTypePrefix();
                    String baseName = sn.getBaseTypeName();
                    newTypes.add(resolveBaseTypedef(prefix, baseName, 
                                       typeSchemaNode.getModule()));
                } else {
                    newTypes.add(sn);
                }
            }
            ut.setTypeSchemaNodes(newTypes);
        }
        return Result.CONTINUE;
    }
    
}
