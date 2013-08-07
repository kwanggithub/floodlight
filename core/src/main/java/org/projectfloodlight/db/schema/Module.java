package org.projectfloodlight.db.schema;

import java.util.Map;

public interface Module {
    public ModuleIdentifier getId();
    public Map<String,TypedefSchemaNode> getTypedefs();
    public Map<String, GroupingSchemaNode> getGrouping();
}
