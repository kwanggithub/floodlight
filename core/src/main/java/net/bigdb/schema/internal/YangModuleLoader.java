package net.bigdb.schema.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import net.bigdb.BigDBException;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.ExtensionSchemaNode;
import net.bigdb.schema.GroupingResolver;
import net.bigdb.schema.GroupingSchemaNode;
import net.bigdb.schema.InvalidSchemaTypeException;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.Module;
import net.bigdb.schema.ModuleIdentifier;
import net.bigdb.schema.ScalarSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.SchemaNodeTypeConflictException;
import net.bigdb.schema.TypeSchemaNode;
import net.bigdb.schema.TypedefResolver;
import net.bigdb.schema.TypedefSchemaNode;
import net.bigdb.schema.UnionTypeSchemaNode;
import net.bigdb.schema.UsesSchemaNode;
import net.bigdb.yang.Configable;
import net.bigdb.yang.ContainerStatement;
import net.bigdb.yang.DataStatement;
import net.bigdb.yang.DataStatementVisitor;
import net.bigdb.yang.Describable;
import net.bigdb.yang.ExtensionStatement;
import net.bigdb.yang.GroupingStatement;
import net.bigdb.yang.ImportStatement;
import net.bigdb.yang.IncludeStatement;
import net.bigdb.yang.LeafListStatement;
import net.bigdb.yang.LeafStatement;
import net.bigdb.yang.ListStatement;
import net.bigdb.yang.ModuleStatement;
import net.bigdb.yang.Statement;
import net.bigdb.yang.Statusable;
import net.bigdb.yang.TypeStatement;
import net.bigdb.yang.Typeable;
import net.bigdb.yang.TypedefStatement;
import net.bigdb.yang.UnknownStatement;
import net.bigdb.yang.UsesStatement;
import net.bigdb.yang.YangSchemaParsingException;
import net.bigdb.yang.parser.YangLexer;
import net.bigdb.yang.parser.YangParser;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

/**
 * This class converts from the root schema node of the YANG parser
 * output to the internal BigDB schema representation. It uses the
 * visitor pattern to walk the YANG tree nodes and map to the analogous
 * BigDB schema nodes.
 * 
 * @author rob.vaterlaus@bigswitch.com
 */
class YangModuleLoader implements ModuleLoader {

    private static String YANG_FILE_EXTENSION = "yang";
    
    @Override
    public String getModuleFileName(ModuleIdentifier moduleId) {
        String fileName = moduleId.getName();
        if (moduleId.getRevision() != null)
            fileName = fileName + "@" + moduleId.getRevision();
        fileName = fileName + "." + YANG_FILE_EXTENSION;
        return fileName;
    }

    @Override
    public ModuleImpl loadModule(SchemaImpl schema,
            ModuleIdentifier moduleId, InputStream inputStream)
            throws BigDBException {
        
        try {
            // FIXME: Should we use an explicit character encoding here?
            ANTLRInputStream input = new ANTLRInputStream(inputStream);
            YangLexer lexer = new YangLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            YangParser parser = new YangParser(tokens);
            
            // Parse the top-level module statement
            ModuleStatement moduleStatement = parser.moduleStatement();
            
            if (parser.getErrors() != null && parser.getErrors().size() > 0) {
                throw new YangSchemaParsingException(
                                "Failed to parse module: " + moduleId.toString(),
                                parser.getErrors());
            }
    
            // Create the instance of the internal module representation
            ModuleImpl moduleImpl = new ModuleImpl(moduleId,
                    moduleStatement.getPrefix());
            
            // Handle the module imports
            for (ImportStatement importStatement: moduleStatement.getImports()) {
                String prefix = importStatement.getPrefix();
                String name = importStatement.getName();
                String revisionDate = importStatement.getRevisionDate();
                ModuleIdentifier importedModuleId =
                        new ModuleIdentifier(name, revisionDate);
                ModuleImpl importedModule =
                        schema.loadModule(importedModuleId);
                schema.addModule(importedModule);
                moduleImpl.importedModules.put(prefix, importedModule);
            }
            
            // Handle the submodule includes
            for (IncludeStatement includeStatement: moduleStatement.getIncludes()) {
                String name = includeStatement.getName();
                String revisionDate = includeStatement.getRevisionDate();
                ModuleIdentifier includedModuleId =
                        new ModuleIdentifier(name, revisionDate);
                ModuleImpl includedModule =
                        schema.loadModule(includedModuleId);
                moduleImpl.includeSubmodule(includedModule);
            }
            
            // Handle the extensions
            for (ExtensionStatement extension
                    : moduleStatement.getExtensions().values()) {
                ExtensionSchemaNode schemaNode =
                        new ExtensionSchemaNode(extension.getName(),
                        extension.getArgument(), moduleImpl.getId());
                initCommonSchemaNodeState(schemaNode, extension, moduleImpl);
                moduleImpl.addExtension(schemaNode);
            } 
            
            // Handle the typedefs
            for (TypedefStatement typedef: moduleStatement.getTypedefs()) {
                String name = typedef.getName();
                TypedefSchemaNode typedefSchemaNode =
                        new TypedefSchemaNode(name, moduleId);
                initScalarSchemaNode(typedefSchemaNode, typedef, moduleImpl);
                initCommonSchemaNodeState(typedefSchemaNode, typedef, moduleImpl);
                moduleImpl.addTypedef(typedefSchemaNode);
            }
            
            // Handle the grouping definitions
            for (GroupingStatement grouping
                    : moduleStatement.getGroupingStatements()) {
                GroupingSchemaNode groupingSchemaNode =
                        createGroupingSchemaNode(moduleImpl, grouping);
                moduleImpl.addGrouping(groupingSchemaNode);
            } 
            
            AggregateSchemaNode schemaRoot = schema.getSchemaRoot();
            
            // Extract the schema info from the module
            // FIXME: Should parse/extract more info here
            for (DataStatement dataStatement:
                    moduleStatement.getDataStatements()) {
                Visitor visitor = new Visitor(moduleImpl, schemaRoot);
                dataStatement.accept(visitor);                
            }
            
            // we have the whole schema, typedefs and groupings for the current
            // module. Resolve typedefs and grouping here.
            Map<ModuleIdentifier, Module> modules = 
                    new HashMap<ModuleIdentifier, Module>(schema.getModules());
            modules.put(moduleImpl.getId(), moduleImpl);
            // resolve typdefs first
            TypedefResolver typedefResolver = new TypedefResolver(modules);
            for (TypedefSchemaNode typedefSchemaNode:
                    moduleImpl.getTypedefs().values()) {
                typedefSchemaNode.accept(typedefResolver);
            }
            // resolve the typedefs in grouping so that we do not need to 
            // resolve the same thing multiple times.
            for (GroupingSchemaNode grouping : moduleImpl.getGrouping().values()) {
                grouping.accept(typedefResolver);
            }
            
            SchemaNode schemaNode;
            
            // we are ready to resolve the grouping
            GroupingResolver groupingResolver = new GroupingResolver(modules);
            for (DataStatement dataStatement: moduleStatement.getDataStatements()) {
                schemaNode = schemaRoot.getChildSchemaNode(dataStatement.getName());
                schemaNode.accept(groupingResolver);                
            }
            
            // finally we need to resolve all schema nodes for typedef
            for (DataStatement dataStatement: moduleStatement.getDataStatements()) {
                schemaNode = schemaRoot.getChildSchemaNode(dataStatement.getName());
                schemaNode.accept(typedefResolver);                
            }

            return moduleImpl;
        }
        catch (RecognitionException exc) {
            throw new BigDBException("Error parsing module: " +
                    moduleId.toString(), exc);
        }
        catch (IOException exc) {
            throw new BigDBException("Error reading module: " +
                    moduleId.toString(), exc);
        }
    }
    
    private GroupingSchemaNode createGroupingSchemaNode(
            ModuleImpl moduleImpl, GroupingStatement grouping)
            throws RecognitionException, IOException, BigDBException {
        String name = grouping.getName();
        GroupingSchemaNode groupingSchemaNode =
                new GroupingSchemaNode(name, moduleImpl.getId());
        for (DataStatement statement : grouping.getChildStatements()) {
            Visitor visitor = new Visitor(moduleImpl, groupingSchemaNode);
            statement.accept(visitor);
        }
        initCommonSchemaNodeState(groupingSchemaNode, grouping, moduleImpl);
        return groupingSchemaNode;
    }
    
    private void initCommonSchemaNodeState(SchemaNode schemaNode,
            Statement statement, ModuleImpl module) throws BigDBException {
        // Set Configable info
        if (statement instanceof Configable) {
            Boolean config = ((Configable)statement).getConfig();
            if (config != null) {
                // config is explicitly set
                schemaNode.setAttribute("Config", config.toString());
            }
        }
        
        // Set Statusable info
        if (statement instanceof Statusable) {
            Statement.Status yangStatus = ((Statusable)statement).getStatus();
            if (yangStatus != null) {
                SchemaNode.Status status = null;
                switch (yangStatus) {
                    case CURRENT:
                        status = SchemaNode.Status.CURRENT;
                        break;
                    case OBSOLETE:
                        status = SchemaNode.Status.OBSOLETE;
                        break;
                    case DEPRECATED:
                        status = SchemaNode.Status.DEPRECATED;
                        break;
                }
                schemaNode.setStatus(status);
            }
        }
        
        // Set Describable info
        if (statement instanceof Describable) {
            Describable describable = (Describable) statement;
            // make sure not override the description when the new one is
            // empty.
            if (describable.getDescription() != null &&
                !describable.getDescription().isEmpty() &&
                (schemaNode.getDescription() != null &&
                 describable.getDescription().length() > schemaNode.getDescription().length() ||
                 schemaNode.getDescription() == null)) {
                schemaNode.setDescription(describable.getDescription());
            }
            schemaNode.setReference(describable.getReference());
        }
        
        // handle unknown statement
        for (UnknownStatement node : statement.getUnknownStatements().values()) {
            handleUnknownStatement(schemaNode, node, module);
        }
    }

    private void handleUnknownStatement(SchemaNode parentSchemaNode,
            UnknownStatement node, ModuleImpl module)
            throws BigDBException {
        // we always map the unknown statement as attributes in 
        // the containing schema node
        // Check the current module for the prefix and extension
        String prefix = node.getPrefix();
        String name = node.getName();
        if (prefix == null || prefix.isEmpty()) {
            throw new BigDBException(String.format(
                    "Missing prefix at extension usage. Extension: \"%s\"", name));
        }
        ModuleImpl exModule = null;
        if (module.prefix.equals(prefix)) {
            exModule = module;
        } else {
            // TODO: remove cast
            exModule = (ModuleImpl) module.getImportedModule(prefix);
        }
        if (exModule == null || exModule.getExtension(name) == null) {
            throw new BigDBException(String.format(
                    "Unknown extension: \"%s:%s\". Possibly a missing module import.",
                    prefix, name));
        }
        parentSchemaNode.setAttribute(name, node.getArg());
    }
    
    private void initTypeSchemaNode(TypeSchemaNode typeNode, 
                  TypeStatement typeStatement, ModuleImpl module) 
                  throws BigDBException{

        // initialize the basic fields
        initCommonSchemaNodeState(typeNode, typeStatement, module);
        typeNode.initTypeInfo(typeStatement);
        // handle union type
        if (typeNode.getLeafType() == SchemaNode.LeafType.UNION) {
            for (TypeStatement ts : typeStatement.getUnionTypeStatements()) {
                TypeSchemaNode tn = 
                        TypeSchemaNode.createTypeSchemaNode(
                                           ts.getName(), ts.getPrefix(), 
                                           typeNode.getName(),
                                           typeNode.getModule());
                initTypeSchemaNode(tn, ts, module);
                ((UnionTypeSchemaNode)typeNode).addTypeSchemanNode(tn);
            }
        }
    }
    private void initScalarSchemaNode(ScalarSchemaNode scalarSchemaNode,
            Typeable typeable, ModuleImpl module) throws BigDBException {

        TypeStatement typeStatement = typeable.getType();
        if (typeStatement == null)
            throw new InvalidSchemaTypeException();
        
        String typeName = typeStatement.getName();
        if (typeName == null)
            throw new InvalidSchemaTypeException();
        String defaultStr = typeable.getDefault();
        // set the default value as a string
        // it will be converted to actual value with specific type
        // when we can determine the type of the node.
        
        TypeSchemaNode typeNode = 
                TypeSchemaNode.createTypeSchemaNode(
                                   typeStatement.getName(), typeStatement.getPrefix(), 
                                   scalarSchemaNode.getName(),
                                   scalarSchemaNode.getModule());
        typeNode.setDefaultValueString(defaultStr);
        initTypeSchemaNode(typeNode, typeStatement, module);
        scalarSchemaNode.setBaseTypedef(typeNode);
        // this the node's own type statement with new restrictions (possibly)
        // TODO: consider save type node instead of statement.
        typeNode.setOwnTypeStatement(typeStatement);
    }
    

    class Visitor implements DataStatementVisitor {

        private final ModuleImpl module;
        private final Stack<AggregateSchemaNode> schemaNodeStack =
                new Stack<AggregateSchemaNode>();
        
        public Visitor(ModuleImpl module, AggregateSchemaNode schemaNode) {
            this.module = module;
            schemaNodeStack.push(schemaNode);
        }
        
        @Override
        public void visitEnter(ContainerStatement containerNode)
                throws BigDBException {
            String name = containerNode.getName();
            AggregateSchemaNode parentSchemaNode = schemaNodeStack.peek();
            
            // Check to see if the container node already exists in the schema.
            // This can happen when we're extending an existing model with new
            // nodes from a new schema file/module. If it does exists we just
            // add new child nodes to it; otherwise we create a new schema node.
            SchemaNode schemaNode = parentSchemaNode.getChildSchemaNode(name, false);
            AggregateSchemaNode aggregateSchemaNode;
            if (schemaNode != null) {
                if (schemaNode.getNodeType() != SchemaNode.NodeType.CONTAINER &&
                    schemaNode.getNodeType() != SchemaNode.NodeType.GROUPING) {
                    throw new SchemaNodeTypeConflictException();
                }
                aggregateSchemaNode = (AggregateSchemaNode) schemaNode;
            } else {
                if (containerNode.getStatementType().equals("container")) {
                    aggregateSchemaNode =
                            new ContainerSchemaNode(name, module.getId());
                } else {
                    // must be grouping 
                    // FXIME: are there better ways to avoid this if-then?
                    aggregateSchemaNode =
                            new GroupingSchemaNode(name, module.getId());                    
                } 
                parentSchemaNode.addChildNode(name, aggregateSchemaNode);
            }
            
            initCommonSchemaNodeState(aggregateSchemaNode, containerNode, module);
            
            schemaNodeStack.push(aggregateSchemaNode);
        }
    
        @Override
        public void visitLeave(ContainerStatement containerNode)
                throws BigDBException {
            schemaNodeStack.pop();
        }
    
        @Override
        public void visitEnter(ListStatement listNode) throws BigDBException {
            String name = listNode.getName();
            AggregateSchemaNode parentSchemaNode = schemaNodeStack.peek();
            
            // Check to see if the list node already exists in the schema.
            // This can happen when we're extending an existing model with new
            // nodes from a new schema file/module. If it does exists we just
            // add new child nodes to it; otherwise we create a new schema node.
            ListSchemaNode listSchemaNode;
            ListElementSchemaNode listElementSchemaNode;
            SchemaNode schemaNode = parentSchemaNode.getChildSchemaNode(name, false);
            if (schemaNode != null) {
                if (schemaNode.getNodeType() != SchemaNode.NodeType.LIST)
                    throw new SchemaNodeTypeConflictException();
                listSchemaNode = (ListSchemaNode) schemaNode;
                listElementSchemaNode = listSchemaNode.getListElementSchemaNode();
            } else {
                listElementSchemaNode = new ListElementSchemaNode(
                        module.getId());
                listSchemaNode = new ListSchemaNode(name, module.getId(),
                        listElementSchemaNode);
                parentSchemaNode.addChildNode(name, listSchemaNode);
            }
    
            // Add any node names that contribute to the compound key for the
            // elements in the list
            String key = listNode.getKey();
            if (key != null && !key.isEmpty()) {
                String[] keyNodeNames = key.split("[ \t]+");
                for (String keyNodeName: keyNodeNames) {
                    listElementSchemaNode.addKeyNodeName(keyNodeName);
                }
            }
            initCommonSchemaNodeState(listSchemaNode, listNode, module);
    
            schemaNodeStack.push(listElementSchemaNode);
        }
    
        @Override
        public void visitLeave(ListStatement listNode) throws BigDBException {
            schemaNodeStack.pop();
        }

        @Override
        public void visit(LeafStatement leafStatement) throws BigDBException {
            String name = leafStatement.getName();
            AggregateSchemaNode parentSchemaNode = schemaNodeStack.peek();
            LeafSchemaNode leafSchemaNode =
                    new LeafSchemaNode(name, module.getId());
            initScalarSchemaNode(leafSchemaNode, leafStatement, module);
            initCommonSchemaNodeState(leafSchemaNode, leafStatement, module);
            leafSchemaNode.setMandatory(
                             leafStatement.getMandatory() != null ?
                                 leafStatement.getMandatory().booleanValue() : 
                                 false);
            parentSchemaNode.addChildNode(name, leafSchemaNode);
        }
    
        @Override
        public void visit(LeafListStatement leafListStatement)
                throws BigDBException {
            String name = leafListStatement.getName();
            AggregateSchemaNode parentSchemaNode =
                    schemaNodeStack.peek();
            LeafSchemaNode leafSchemaNode =
                    new LeafSchemaNode("", module.getId());
            initScalarSchemaNode(leafSchemaNode, leafListStatement, module);
            LeafListSchemaNode leafListSchemaNode = new LeafListSchemaNode(name,
                    module.getId(), leafSchemaNode);
            initCommonSchemaNodeState(leafListSchemaNode, leafListStatement, module);
            parentSchemaNode.addChildNode(name, leafListSchemaNode);
        }

        @Override
        public void visit(UsesStatement usesNode) throws BigDBException {
            String name = usesNode.getName();
            AggregateSchemaNode parentSchemaNode = schemaNodeStack.peek();
            UsesSchemaNode usesSchemaNode =
                    new UsesSchemaNode(name, usesNode.getPrefix(), module.getId());
            initCommonSchemaNodeState(usesSchemaNode, usesNode, module);
            // Simply add ueseNode as a child here
            // Will replace it with its contents later
            parentSchemaNode.addChildNode(name, usesSchemaNode);
        }

        @Override
        public void visit(UnknownStatement node) throws BigDBException {
            // Always map the unknown statement that correspond to defined
            // extensions to attributes in the parent schema node.
            // Check the current module for the prefix and extension
            String prefix = node.getPrefix();
            String name = node.getName();
            if (prefix == null || prefix.isEmpty()) {
                // Unknown statements corresponding to extensions statement
                // are required to include the prefix of the module where
                // the extension is defined, so if it doesn't contain a
                // prefix then it's not an extension, so we ignore it.
                // FIXME: Should maybe log a warning here.
                return;
            }
            
            // Make sure there's a corresponding extension definition
            ModuleImpl exModule = null;
            if (module.getPrefix().equals(prefix)) {
                exModule = module;
            } else {
                // TODO: remove cast
                exModule = (ModuleImpl) module.getImportedModule(prefix);
            }
            if (exModule == null || exModule.getExtension(name) == null) {
                // Extension is not defined, ignore.
                // FIXME: Should maybe log a warning here
                return;
            }
            
            SchemaNode parentSchemaNode = schemaNodeStack.peek();
            // FIXME: Should maybe not always set an attribute here.
            // Maybe only if it's a recognized unknown statement that we
            // explicitly want to map to an attribute.
            parentSchemaNode.setAttribute(prefix + ":" + name,
                    node.getArg());
        }

        @Override
        public void visit(ExtensionStatement node) throws BigDBException {
            String name = node.getName();
            ExtensionSchemaNode schemaNode = new ExtensionSchemaNode(
                    name, node.getArgument(), module.getId());
            initCommonSchemaNodeState(schemaNode, node, module);
            module.addExtension(schemaNode);
        }
    }
}
