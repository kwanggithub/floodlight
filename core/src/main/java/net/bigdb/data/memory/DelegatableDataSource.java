package net.bigdb.data.memory;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataSource;
import net.bigdb.schema.SchemaNode;

public interface DelegatableDataSource extends DataSource {
    public void setRoot(DataNode root) throws BigDBException;
    public SchemaNode getRootSchemaNode() throws BigDBException;
}