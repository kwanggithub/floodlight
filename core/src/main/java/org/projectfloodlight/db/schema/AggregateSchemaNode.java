package org.projectfloodlight.db.schema;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.projectfloodlight.db.BigDBException;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class AggregateSchemaNode extends SchemaNode {

    protected SortedMap<String, SchemaNode> childNodes =
            new TreeMap<String,SchemaNode>();
    
    public AggregateSchemaNode() {
        this(null, null, null);
    }
    
    public AggregateSchemaNode(String name, ModuleIdentifier module,
            NodeType nodeType) {
        super(name, module, nodeType);
    }
    
    public void addChildNode(String name, SchemaNode childNode) {
        boolean config = this.getActualConfig();
        try {
            childNode.checkConfigurableConflicts(this, config);
        } catch (SchemaNodeConfigurableConflictException e) {
            // not throw checked exception
            // TODO: cleanup exceptions
            throw new RuntimeException(e);
        }
        childNode.parent = this;
        childNodes.put(name, childNode);
    }
    
    @Override
    public void checkConfigurableConflicts(SchemaNode parent, 
                                                 boolean parentActual) 
        throws SchemaNodeConfigurableConflictException {
        // check itself and parent
        super.checkConfigurableConflicts(parent, parentActual);
        // check children
        for (Map.Entry<String, SchemaNode> e : this.getChildNodes().entrySet()) {
            String configString = this.getLocalConfigSetting();
            boolean config = true;
            if (parentActual) {
                parent = this;
                if (configString != null && configString.equals("false")) {
                    config = false;
                }
            } else {
                config = false;
            }
            e.getValue().checkConfigurableConflicts(parent, config);
        }
    }
    
    public void removeChildNode(String name) {
        SchemaNode removedNode = childNodes.remove(name);
        removedNode.parent = null;
    }
    
    public SortedMap<String, SchemaNode> getChildNodes() {
        return Collections.unmodifiableSortedMap(childNodes);
    }
    
    @JsonIgnore
    @Override
    public SchemaNode getChildSchemaNode(String childNodeName, boolean toThrow)
            throws BigDBException {
        SchemaNode childSchemaNode = childNodes.get(childNodeName);
        if (childSchemaNode == null && toThrow) {
            String message = String.format("Schema node \"%s\": child \"%s\" not found",
                    getName(), childNodeName);
            throw new SchemaNodeNotFoundException(childNodeName, message);
        }
        return childSchemaNode;
    }
    
    @Override
    public Set<String> getDataSourcesWithDefaultContent() {
        Set<String> dataSources = new HashSet<String>();
        for (SchemaNode childSchemaNode: childNodes.values()) {
            Set<String> childDataSources =
                    childSchemaNode.getDataSourcesWithDefaultContent();
            dataSources.addAll(childDataSources);
        }
        return dataSources;
    }

    protected SchemaNodeVisitor.Result acceptChildNodes(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = SchemaNodeVisitor.Result.CONTINUE;
        for (SchemaNode childNode: childNodes.values()) {
            SchemaNodeVisitor.Result childResult = childNode.accept(visitor);
            if (childResult == SchemaNodeVisitor.Result.TERMINATE) {
                result = SchemaNodeVisitor.Result.TERMINATE;
                break;
            }
            if (childResult == SchemaNodeVisitor.Result.SKIP_SIBLINGS) {
                break;
            }
        }
        return result;
    }

    @Override
    public SchemaNode clone() {
        AggregateSchemaNode schemaNode = (AggregateSchemaNode) super.clone();
        // Make a deep copy of the child node map
        schemaNode.childNodes = new TreeMap<String, SchemaNode>();
        for (Map.Entry<String, SchemaNode> entry : childNodes.entrySet()) {
            schemaNode.addChildNode(entry.getKey(), entry.getValue().clone());
        }
        return schemaNode;
    }
}
