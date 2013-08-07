package org.projectfloodlight.db.yang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleStatementCommon extends Statement
        implements Nameable, Describable {

    protected String name;
    protected String yangVersion;
    protected String organization;
    protected String contact;
    protected String description;
    protected String reference;
    protected List<RevisionStatement> revisions =
            new ArrayList<RevisionStatement>();;
    protected List<ImportStatement> imports =
            new ArrayList<ImportStatement>();
    protected List<IncludeStatement> includes =
            new ArrayList<IncludeStatement>();
    protected List<DataStatement> dataStatements =
            new ArrayList<DataStatement>();
    protected List<TypedefStatement> typedefs =
            new ArrayList<TypedefStatement>();

    protected List<GroupingStatement> groupings =
            new ArrayList<GroupingStatement>(); 
    
    protected Map<String, ExtensionStatement> extensions =
            new HashMap<String, ExtensionStatement>();
    
    public ModuleStatementCommon() {
        this(null);
    }
    
    public ModuleStatementCommon(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public String getYangVersion() {
        return yangVersion;
    }
    
    public String getOrganization() {
        return organization;
    }
    
    public String getContact() {
        return contact;
    }
    
    public String getDescription() {
        return description;
    }
    
    public String getReference() {
        return reference;
    }
    
    public List<RevisionStatement> getRevisions() {
        return revisions;
    }
    
    public List<ImportStatement> getImports() {
        return imports;
    }
    
    public List<IncludeStatement> getIncludes() {
        return includes;
    }
    
    public List<TypedefStatement> getTypedefs() {
        return typedefs;
    }
    
    public List<DataStatement> getDataStatements() {
        return dataStatements;
    }
    
    public List<GroupingStatement> getGroupingStatements() {
        return this.groupings;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public void setYangVersion(String yangVersion) {
        this.yangVersion = yangVersion;
    }
    
    public void setOrganization(String organization) {
        this.organization = organization;
    }
    
    public void setContact(String contact) {
        this.contact = contact;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public void setReference(String reference) {
        this.reference = reference;
    }
    
    public void addRevision(RevisionStatement revision) {
        revisions.add(revision);
    }
    
    public void addImport(ImportStatement imp) {
        imports.add(imp);
    }
    
    public void addInclude(IncludeStatement include) {
        includes.add(include);
    }
    
    public void addTypedef(TypedefStatement typedef) {
        typedefs.add(typedef);
    }
    
    public void addDataStatement(DataStatement dataStatement) {
        this.dataStatements.add(dataStatement);
    }
    
    public void addGroupingStatement(GroupingStatement groupingStatement) {
        this.groupings.add(groupingStatement);
    }
    
    public ExtensionStatement getExtensionStatement(String name) {
        return this.extensions.get(name);
    }
    
    public void addExtension(ExtensionStatement ex) {
        this.extensions.put(ex.getName(), ex);
    }
    
    public Map<String, ExtensionStatement> getExtensions() {
        return this.extensions;
    }
}
