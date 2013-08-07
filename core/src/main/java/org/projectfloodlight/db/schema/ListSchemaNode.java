package org.projectfloodlight.db.schema;

import java.util.Collections;
import java.util.List;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.data.IndexSpecifier;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ListSchemaNode extends SchemaNode {

    protected ListElementSchemaNode listElementSchemaNode;
    
    public ListSchemaNode() {
    }
    
    public ListSchemaNode(String name, ModuleIdentifier moduleId,
            ListElementSchemaNode listElementSchemaNode) {
        super(name, moduleId, NodeType.LIST);
        this.listElementSchemaNode = listElementSchemaNode;
        listElementSchemaNode.parent = this;
    }
    
    public ListElementSchemaNode getListElementSchemaNode() {
        return listElementSchemaNode;
    }
    
    @JsonIgnore
    public List<String> getKeyNodeNames() {
        return (listElementSchemaNode != null) ?
                listElementSchemaNode.getKeyNodeNames() :
                Collections.<String>emptyList();
    }
    
    @JsonIgnore
    public IndexSpecifier getKeySpecifier() {
        List<String> keyNodeNames = getKeyNodeNames();
        if (keyNodeNames.isEmpty())
            return null;

        IndexSpecifier.Builder builder = new IndexSpecifier.Builder(true);
        for (String keyName: keyNodeNames) {
            try {
                // FIXME: Should really change getChildSchemaNode to use an
                // uncatched BigDBInternal error instead of a catched exception
                SchemaNode keySchemaNode = getChildSchemaNode(keyName, false);
                boolean caseSensitive = keySchemaNode.getBooleanAttributeValue(
                        SchemaNode.CASE_SENSITIVE_ATTRIBUTE_NAME, true);
                builder.addField(keyName, IndexSpecifier.SortOrder.FORWARD,
                        caseSensitive);
            }
            catch (BigDBException e) {
                throw new BigDBInternalError("Invalid key name: " + keyName, e);
            }
        }
        return builder.getIndexSpecifier();
    }

    @JsonIgnore
    public boolean isKeyedList() {
        return !getKeyNodeNames().isEmpty();
    }
    
    @Override
    public SchemaNode getChildSchemaNode(String name, boolean toThrow)
            throws BigDBException {
        return listElementSchemaNode.getChildSchemaNode(name, toThrow);
    }

    @Override
    public SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException {
        SchemaNodeVisitor.Result result = visitor.visitEnter(this);
        if (result == SchemaNodeVisitor.Result.TERMINATE)
            return SchemaNodeVisitor.Result.TERMINATE;
        if (result != SchemaNodeVisitor.Result.SKIP_SUBTREE) {
            result = listElementSchemaNode.accept(visitor);
            if (result == SchemaNodeVisitor.Result.TERMINATE)
                return SchemaNodeVisitor.Result.TERMINATE;
        }
        result = visitor.visitLeave(this);
        return result;
    }
    
    @Override
    public ListSchemaNode clone() {
        ListSchemaNode schemaNode = (ListSchemaNode) super.clone();
        schemaNode.listElementSchemaNode = (ListElementSchemaNode)
                listElementSchemaNode.clone();
        schemaNode.listElementSchemaNode.parent = schemaNode;
        return schemaNode;
    }
}
