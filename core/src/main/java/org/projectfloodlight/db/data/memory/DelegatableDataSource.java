package org.projectfloodlight.db.data.memory;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataSource;
import org.projectfloodlight.db.schema.SchemaNode;

public interface DelegatableDataSource extends DataSource {
    public void setRoot(DataNode root) throws BigDBException;
    public SchemaNode getRootSchemaNode() throws BigDBException;
}