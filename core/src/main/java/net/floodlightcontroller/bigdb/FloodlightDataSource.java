package net.floodlightcontroller.bigdb;

import net.bigdb.BigDBException;
import net.bigdb.data.ServerDataSource;
import net.bigdb.schema.Schema;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class FloodlightDataSource extends ServerDataSource {

    public FloodlightDataSource(FloodlightModuleContext context, Schema schema)
            throws BigDBException  {
        super("controller-data-source", schema);
        FloodlightResource.setModuleContext(context);
    }
}
