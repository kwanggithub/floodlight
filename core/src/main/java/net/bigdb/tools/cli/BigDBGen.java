package net.bigdb.tools.cli;


import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.config.ModuleConfig;
import net.bigdb.config.RootConfig;
import net.bigdb.schema.internal.SchemaImpl;
import net.bigdb.service.internal.ServiceImpl;
import net.bigdb.tools.docgen.ConfluenceDocGenerator;
import net.floodlightcontroller.bigdb.EmbeddedBigDBService;

public class BigDBGen {
    protected final SchemaImpl schema = new SchemaImpl();

    // Borrow some functionalities from ServiceImpl without running the service.
    protected final ServiceImpl service = new ServiceImpl();

    List<ModuleConfig> moduleConfig = new ArrayList<>();

    public BigDBGen(String config) throws BigDBException {
        service.initializeFromResource(config);
    }

    public BigDBGen() throws BigDBException {
        RootConfig rc = 
                EmbeddedBigDBService.getDefaultConfig(moduleConfig, null);
        service.initializeFromRootConfig(rc);        
    }

    public void addModuleSchema(String name) {
        addModuleSchema(name, null);
    }

    public void addModuleSchema(String name, String revision) {
        ModuleConfig c = new ModuleConfig();
        c.name = name;
        c.revision = revision;
        moduleConfig.add(c);
    }
    
    public void generateDoc(String sampleFile, String path)
            throws BigDBException {
        ConfluenceDocGenerator codeGen = new ConfluenceDocGenerator(sampleFile, path);
        ((SchemaImpl)service.getTreespace("controller").getSchema()).getSchemaRoot().accept(codeGen);
    }

}
