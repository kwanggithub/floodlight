package org.projectfloodlight.db.config;

import static org.junit.Assert.*;

import org.junit.Test;
import org.projectfloodlight.db.config.ConfigException;
import org.projectfloodlight.db.config.DataSourceConfig;
import org.projectfloodlight.db.config.DataSourceMappingConfig;
import org.projectfloodlight.db.config.ModuleConfig;
import org.projectfloodlight.db.config.ModuleSearchPathConfig;
import org.projectfloodlight.db.config.RootConfig;
import org.projectfloodlight.db.config.TreespaceConfig;

public class ConfigTest {
    
    public void checkConfig(RootConfig actualRootConfig,
            RootConfig expectedRootConfig) {
        assertEquals(actualRootConfig, expectedRootConfig);
        if (actualRootConfig != null) {
            assertEquals(actualRootConfig.hashCode(),
                    expectedRootConfig.hashCode());
        }
    }
    
    public void doConfigString(String configString,
            RootConfig expectedRootConfig) throws ConfigException {
        RootConfig actualRootConfig = RootConfig.loadConfigString(configString);
        checkConfig(actualRootConfig, expectedRootConfig);
    }

    public void doConfigResource(String resourceName,
            RootConfig expectedRootConfig) throws ConfigException {
        RootConfig actualRootConfig = RootConfig.loadConfigResource(
                "/org/projectfloodlight/db/config/" + resourceName);
        checkConfig(actualRootConfig, expectedRootConfig);
    }

    public void doExpectedFailureConfigString(String configString,
            String expectedMessage) {
        try {
            RootConfig.loadConfigString(configString);
            fail("Expected exception during config load!");
        }
        catch (ConfigException exc) {
            assertEquals(exc.getMessage(), expectedMessage);
        }
    }
    
    @Test
    public void testEmptyConfig() throws ConfigException {
        doConfigString("", null);
    }
    
    @Test
    public void testConfig1() throws ConfigException {
                
        RootConfig expectedRootConfig = new RootConfig();
        TreespaceConfig treespaceConfig = new TreespaceConfig();
        treespaceConfig.name = "controller";
        expectedRootConfig.treespaces.add(treespaceConfig);
        
        DataSourceConfig dataSourceConfig = new DataSourceConfig();
        dataSourceConfig.name = "config";
        dataSourceConfig.implementation_class =
                "net.aurora.datasource.cassandra.CassandraDataSource";
        dataSourceConfig.properties.put("keyspace", "config");
        dataSourceConfig.properties.put("read_consistency_level", "ONE");
        dataSourceConfig.properties.put("write_consistency_level", "ONE");
        dataSourceConfig.properties.put("durable_writes", "true");
        treespaceConfig.data_sources.add(dataSourceConfig);
        
        DataSourceConfig dataSourceConfig2 = new DataSourceConfig();
        dataSourceConfig2.name = "operational";
        dataSourceConfig2.implementation_class =
                "net.aurora.datasource.cassandra.CassandraDataSource";
        dataSourceConfig2.properties.put("keyspace", "operational");
        dataSourceConfig2.properties.put("durable_writes", "false");
        treespaceConfig.data_sources.add(dataSourceConfig2);

        DataSourceMappingConfig configDataSourceMapping =
                new DataSourceMappingConfig();
        configDataSourceMapping.data_source = "config";
        configDataSourceMapping.predicate = "Config";
        
        DataSourceMappingConfig defaultDataSourceMapping =
                new DataSourceMappingConfig();
        defaultDataSourceMapping.data_source = "operational";
                
        treespaceConfig.data_source_mappings.add(configDataSourceMapping);
        treespaceConfig.data_source_mappings.add(defaultDataSourceMapping);

        ModuleSearchPathConfig moduleSearchPathConfig =
                new ModuleSearchPathConfig();
        moduleSearchPathConfig.path = "test-modules";
        moduleSearchPathConfig.recursive = true;
        treespaceConfig.module_search_paths.add(moduleSearchPathConfig);
        
        ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.name = "bvs";
        moduleConfig.revision = "2012-07-10";
        moduleConfig.directory = "test/schemas";
        treespaceConfig.modules.add(moduleConfig);
        
        doConfigResource("TestConfig1.yaml", expectedRootConfig);
    }
    
    @Test
    public void testInvalidConfigSyntax() {
        String configString = "bad yaml - junky\n";
        doExpectedFailureConfigString(configString,
            RootConfig.LOAD_DESERIALIZE_ERROR_MESSAGE);
    }
    
    @Test
    public void testInvalidConfigMapping() {
        String configString =
            "treespace:\n" +
            "  - invalid-field: foobar\n";
        doExpectedFailureConfigString(configString,
            RootConfig.LOAD_DESERIALIZE_ERROR_MESSAGE);
    }
    
    @Test
    public void testBadTreespaceName() throws ConfigException {
        String configString =
            "treespaces:\n" +
            "  - name: \"0hello(\"\n";
        doExpectedFailureConfigString(configString,
            TreespaceConfig.INVALID_TREESPACE_NAME_ERROR_MESSAGE);
    }
    
    @Test
    public void testBadDataSourceName() throws ConfigException {
        String configString =
            "treespaces:\n" +
            "  - name: controller\n" +
            "    data_sources:\n" +
            "      - name: 345677\n";
        doExpectedFailureConfigString(configString,
            DataSourceConfig.INVALID_DATA_SOURCE_NAME_ERROR_MESSAGE);
    }
}
