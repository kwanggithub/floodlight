package net.bigdb.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.bigdb.BigDBException;
import net.bigdb.schema.internal.ModuleImpl;

public class GroupingResolver extends AbstractSchemaNodeVisitor {
    
    private final Map<ModuleIdentifier, Module> modules;
    
    public GroupingResolver(Map<ModuleIdentifier, Module> modules) {
        this.modules = modules;
    }
    
    private Result
        visitAggregateNode(AggregateSchemaNode containerSchemaNode) 
                throws BigDBException {
        Map<String, SchemaNode> children = 
                containerSchemaNode.getChildNodes();
        List<String> usesNodes = new ArrayList<String>();
        for (Map.Entry<String, SchemaNode> e : children.entrySet()) {
            if (e.getValue().getNodeType() == SchemaNode.NodeType.USES) {
                usesNodes.add(e.getKey());
            }
        }
        // resolve all uses.
        for (String groupingName : usesNodes) {
            UsesSchemaNode unode = (UsesSchemaNode)containerSchemaNode.
                    getChildSchemaNode(groupingName);
            // remove the use node
            containerSchemaNode.removeChildNode(groupingName);
            
            // This is not defined in yang specification
            // If the using node does not have a description, the description
            // of the grouping will be used.
            // FIXME: RobV: Not sure if it should do this.
            // Seems like it would work in some, but not all, cases.
            SchemaNode theNode = containerSchemaNode;
            if (containerSchemaNode.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
                theNode = containerSchemaNode.getParentSchemaNode();
            }
            String description = theNode.getDescription();
            if (usesNodes.size() == 1 && 
                (description == null || description.isEmpty())) {
                theNode.setDescription(unode.description);
            }
            // Here it is assumed that all used nodes in this usesNode have been
            // resolved.
            // add child one by one to make parent is set.
            for (Map.Entry<String, SchemaNode> e : 
                unode.getUsedSchemaNodes().entrySet()) {
                containerSchemaNode.addChildNode(e.getValue().getName(),
                                                 e.getValue());
            }
        }
        return Result.CONTINUE;
    }

    @Override 
    public Result
    visitLeave(ContainerSchemaNode containerSchemaNode) 
            throws BigDBException {
        return visitAggregateNode(containerSchemaNode);
    }
    
    @Override
    public Result
    visitLeave(GroupingSchemaNode groupingSchemaNode) 
            throws BigDBException {
        return visitAggregateNode(groupingSchemaNode);
    }

    @Override
    public Result
    visitLeave(ListSchemaNode listSchemaNode) 
            throws BigDBException {
        // FIXME: robv: Not sure if this is correct!
        return visitAggregateNode(listSchemaNode.getListElementSchemaNode());
    }

    @Override
    public Result
    visit(UsesSchemaNode usesSchemaNode) throws BigDBException {
        // here actually resolve the links
        String groupingName = usesSchemaNode.getName();
        if (groupingName != null) {
            ModuleIdentifier moduleId = usesSchemaNode.getModule();
            ModuleImpl module = (ModuleImpl) modules.get(moduleId);
            if (module == null)
                throw new ModuleNotFoundException(moduleId);
            String prefix = usesSchemaNode.getPrefix();
            // FIXME: is it correct that use the prefix for the same
            // containing module?
            Module sourceModule = null;
            if (prefix != null && !prefix.isEmpty()) {
                if (prefix.equals(module.getPrefix())) {
                    sourceModule = module;
                } else {
                    sourceModule = module.getImportedModule(prefix);
                }
            } else {
                sourceModule = module;
            }
            if (sourceModule == null)
                throw new ModulePrefixNotFoundException(prefix);
            GroupingSchemaNode groupingSchemaNode =
                    sourceModule.getGrouping().get(groupingName);
            if (groupingSchemaNode == null)
                throw new TypedefNotFoundException(groupingName);
            // resolve the grouping node
            // this resolved the grouping in the same module as the current
            // grouping definition. 
            groupingSchemaNode.accept(this);
            // grouping is resolved.
            if (usesSchemaNode.getDescription() == null || 
                usesSchemaNode.getDescription().isEmpty()) {
                usesSchemaNode.setDescription(groupingSchemaNode.getDescription());
            }
            groupingSchemaNode.copyChildNodes(usesSchemaNode.getUsedSchemaNodes());
        }
        return Result.CONTINUE;
    }
}
