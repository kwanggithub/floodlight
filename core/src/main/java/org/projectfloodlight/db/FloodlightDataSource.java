package org.projectfloodlight.db;

import org.projectfloodlight.core.module.FloodlightModuleContext;
import org.projectfloodlight.db.data.ServerDataSource;
import org.projectfloodlight.db.schema.Schema;

public class FloodlightDataSource extends ServerDataSource {

    public FloodlightDataSource(FloodlightModuleContext context, Schema schema)
            throws BigDBException  {
        super("controller-data-source", schema);
        FloodlightResource.setModuleContext(context);
    }
}
