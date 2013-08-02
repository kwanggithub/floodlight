package net.bigdb.yang;

public interface Typeable {
    public TypeStatement getType();
    public String getUnits();
    public String getDefault();
    
    public void setType(TypeStatement typeDescriptor);
    public void setUnits(String units);
    public void setDefault(String defaultValue);
}
