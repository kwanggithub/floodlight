package org.projectfloodlight.db.schema;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.util.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SchemaNode implements Cloneable {
    protected final static Logger logger = LoggerFactory.getLogger(SchemaNode.class);

    public enum NodeType {
        CONTAINER,
        LIST,
        LIST_ELEMENT,
        LEAF,
        LEAF_LIST,
        REFERENCE,
        GROUPING,
        USES,            //temp type until it is resolved.
        TYPE
    };

    public enum LeafType {
        NEED_RESOLVE,       // temp type until it is resolved.
        BOOLEAN,
        INTEGER,
        DECIMAL,
        STRING,
        BINARY,
        ENUMERATION,
        UNION,
        LEAF_REF
    }

    public enum Status {
        CURRENT,
        OBSOLETE,
        DEPRECATED
    };

    // FIXME: Should include module prefix in attribute name.
    public static final String CASCADE_ATTRIBUTE_NAME = "cascade";
    public static final String CASE_SENSITIVE_ATTRIBUTE_NAME = "case-sensitive";
    public static final String ALLOW_EMPTY_STRING_ATTRIBUTE_NAME = "allow-empty-string";
    public static final String DATA_SOURCE_ATTRIBUTE_NAME = "data-source";
    public static final String CONFIG_ATTRIBUTE_NAME = "Config";
    public static final String LOCAL_CONFIG_ATTRIBUTE_NAME = "local-config";

    protected static final Map<String, Boolean> attributeInheritanceMap =
            new HashMap<String, Boolean>();

    static {
        attributeInheritanceMap.put(CASCADE_ATTRIBUTE_NAME, Boolean.FALSE);
        attributeInheritanceMap.put(CASE_SENSITIVE_ATTRIBUTE_NAME, Boolean.FALSE);
        attributeInheritanceMap.put(ALLOW_EMPTY_STRING_ATTRIBUTE_NAME, Boolean.FALSE);
        attributeInheritanceMap.put(DATA_SOURCE_ATTRIBUTE_NAME, Boolean.TRUE);
        attributeInheritanceMap.put(CONFIG_ATTRIBUTE_NAME, Boolean.TRUE);
        attributeInheritanceMap.put(LOCAL_CONFIG_ATTRIBUTE_NAME, Boolean.TRUE);
    }

    protected String name;
    protected SchemaNode parent;
    protected ModuleIdentifier module;
    protected NodeType nodeType;
    protected boolean mandatory = false;
    protected Status status;
    protected String description;
    protected String reference;
    protected Map<String, String> attributes = new HashMap<String, String>();

    // FIXME: Ideally the data sources field shouldn't be in this class,
    // since, in theory, all of the schema loader functionality should be
    // usable apart from the rest of BigDB for things like code generation
    // and documentation generation. There are different ways we could handle
    // that. One way would be to create schema nodes via a factory and if a
    // specific application needs to attach additional state to a schema node
    // it can subclass the different schema node classes with additional data
    // members and then subclass the factory class to create the
    // application-specific schema node subclasses. Another way (a little less
    // clean but easier to implement) would be to just have an "app data"
    // data member of type "Object" that an application could use however it
    // wants and use casting. But for now we'll just keep things simple and
    // just target the normal BigDB operation.
    protected Set<String> dataSources = new HashSet<String>();

    public SchemaNode() {
    }

    public SchemaNode(String name, ModuleIdentifier module) {
        this.name = name;
        this.module = module;
    }

    public SchemaNode(String name, ModuleIdentifier module, NodeType nodeType) {
        this.name = name;
        this.module = module;
        this.nodeType = nodeType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ModuleIdentifier getModule() {
        return module;
    }

    public void setModule(ModuleIdentifier module) {
        this.module = module;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public String getAttributeStringValue(String name) {
        return getAttribute(name);
    }

    public String getStringAttributeValue(String name, String defaultValue) {
        String value = getAttribute(name);
        return (value != null) ? value : defaultValue;
    }

    public boolean getBooleanAttributeValue(String name, boolean defaultValue) {
        String value = getAttribute(name);
        return (value != null) ? Boolean.parseBoolean(value.toLowerCase())
                : defaultValue;
    }

    @JsonIgnore
    public String getAttribute(String name) {
        String value = attributes.get(name);
        if (value == null) {
            Boolean inheritable = attributeInheritanceMap.get(name);
            if ((parent != null) && (inheritable != null) && inheritable.booleanValue())
                value = parent.getAttribute(name);
        }
        return value;
    }

    @JsonIgnore
    public SchemaNode getParentSchemaNode() {
        return parent;
    }

    public boolean isAncestorSchemaNode(SchemaNode schemaNode) {
        SchemaNode ancestorSchemaNode = parent;
        while (ancestorSchemaNode != null) {
            if (ancestorSchemaNode == schemaNode)
                return true;
            ancestorSchemaNode = ancestorSchemaNode.parent;
        }
        return false;
    }

    public void setAttribute(String name, String value) {
        assert name != null;
        assert value != null;
        attributes.put(name, value);
    }

    public Set<String> getDataSources() {
        return dataSources;
    }

    public void resetDataSources() {
        dataSources.clear();
    }

    public void setDataSource(String dataSource) {
        dataSources.clear();
        addDataSource(dataSource);
    }

    public void addDataSource(String dataSource) {
        if (dataSource != null) {
            // FIXME: Is this the right place to do the parent traversal?
            boolean added = dataSources.add(dataSource);
            if(added && logger.isTraceEnabled()) {
                logger.trace(this.toString() + ": added data source "+dataSource);
            }
            if (parent != null)
                parent.addDataSource(dataSource);
        }
    }

    public void addDataSources(Set<String> dataSources) {
        this.dataSources.addAll(dataSources);
    }

    public abstract SchemaNodeVisitor.Result accept(SchemaNodeVisitor visitor)
            throws BigDBException;

    @JsonIgnore
    public SchemaNode getChildSchemaNode(String childNodeName)
            throws BigDBException
    {
        return this.getChildSchemaNode(childNodeName, true);
    }

    @JsonIgnore
    public SchemaNode getDescendantSchemaNode(Path path)
            throws BigDBException {
        SchemaNode schemaNode = this;
        for (String component: path) {
            schemaNode = schemaNode.getChildSchemaNode(component);
        }
        return schemaNode;
    }

    @JsonIgnore
    public SchemaNode getDescendantSchemaNode(String pathString)
            throws BigDBException {
        return getDescendantSchemaNode(new Path(pathString));
    }

    @JsonIgnore
    public SchemaNode getChildSchemaNode(String childNodeName, boolean toThrow)
            throws BigDBException
    {
        if (toThrow) {
            throw new SchemaNodeNotFoundException("Node : " + this.getName() +
                                                  " does not have children.");
        }
        return null;
    }

    /**
     * Returns the list of data source names that contribute default content
     * to an instance of this schema node. This includes any nested containers
     * that have leaf data nodes with default values. It does not include any
     * list nodes, because they need to have explicit list elements to have any
     * content.
     *
     * @return the set of data source names with default content
     */
    @JsonIgnore
    public Set<String> getDataSourcesWithDefaultContent() {
        return Collections.emptySet();
    }

    /**
     * This returns true if all of the schema nodes in the specified path are
     * container nodes (not including the root schema node, i.e. this).
     *
     * @param path
     *            path specifying a descendant schema node. The path should be a
     *            relative path.
     * @return true if all of the schema nodes from the root schema node to the
     *         descendant schema node specified by the path are container schema
     *         nodes; false if any of them are not containers
     * @throws BigDBException
     */
    @JsonIgnore
    public boolean isNestedContainerPath(Path path) throws BigDBException {
        SchemaNode schemaNode = this;
        for (String name: path) {
            schemaNode = schemaNode.getChildSchemaNode(name);
            if (schemaNode.getNodeType() != SchemaNode.NodeType.CONTAINER)
                return false;
        }
        return true;
    }

    @Override
    public SchemaNode clone() {
        try {
            SchemaNode schemaNode = (SchemaNode) super.clone();
            if (schemaNode.attributes != null) {
                schemaNode.attributes = new HashMap<String, String>(attributes);
            }
            schemaNode.dataSources = new HashSet<String>(dataSources);
            return schemaNode;
        }
        catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * This function tries to find out the actual configurable status of a node.
     * It looks at the local setting and return it if there is one. otherwise
     * ask the parent's status.
     *
     * @return whether this node is configurable (true) or not (false).
     */
    @JsonIgnore
    public boolean getActualConfig() {
        String configValue = getAttribute(CONFIG_ATTRIBUTE_NAME);
        if (configValue != null)
            return !configValue.toLowerCase().equals("false");
        if (parent == null) {
            return true;
        } else {
            return parent.getActualConfig();
        }
    }

    @JsonIgnore
    public String getLocalConfigSetting() {
        return attributes.get(CONFIG_ATTRIBUTE_NAME);
    }

    public void checkConfigurableConflicts(SchemaNode parent,
                                                 boolean parentActual)
        throws SchemaNodeConfigurableConflictException {

        if (!parentActual) {
            if (this.getLocalConfigSetting() != null &&
                !this.getLocalConfigSetting().equals("false")) {
                throw new SchemaNodeConfigurableConflictException(parent, this);
            }
        }
    }

    public void validate(DataNode dataNode)
            throws ValidationException, BigDBException {

    }

    @Override
    public String toString() {
        return name;
    }
}
