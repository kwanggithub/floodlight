package org.projectfloodlight.device.tag;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The interface defines access methods for tag properties.
 * A Tag can be uniquely identified with a tuple, (namespace, name, value).
 * @author kjiang
 *
 */
public class DeviceTag {
    public static final String KEY_SEPARATOR = "|";
    public static final String TAG_ASSIGNMENT = "=";
    public static final String Tag_NS_SEPARATOR = ".";
    
    protected String m_namespace;
    protected String m_name;
    protected String m_value;
    protected boolean persist;
    
    /**
     * Construct a tag from a fully qualified tag string in
     * format: "namespace.name=value", where
     * name is a string without "." and "="
     * @param fullName
     * @param persist
     * @throws TagInvalidNamespaceException 
     * @throws TagInvalidValueException 
     * @throws TagInvalidNameException 
     */
    public DeviceTag(String fullName, boolean persist) 
           throws TagInvalidNamespaceException, 
                  TagInvalidValueException, 
                  TagInvalidNameException {
        if (fullName == null) {
            throw new TagInvalidNamespaceException();
        }
        
        // remove white spaces
        String modName = fullName.replaceAll("\\s", "");
        if (modName.indexOf(TAG_ASSIGNMENT) == -1) {
            throw new TagInvalidValueException();
        }
        
        String[] temp1 = modName.split(TAG_ASSIGNMENT);
        int nsSeparatorIndex = temp1[0].lastIndexOf(Tag_NS_SEPARATOR);
        if (nsSeparatorIndex == temp1[0].length()-1) {
            // "." is the last character of the name.
            throw new TagInvalidNameException();
        } else if (nsSeparatorIndex == -1) {
            // "." is not in the name, so namespace is null.
            this.m_namespace = null;
            this.m_name = temp1[0];
        } else {
            this.m_namespace = temp1[0].substring(0, nsSeparatorIndex);
            this.m_name = temp1[0].substring(nsSeparatorIndex+1);
        }
        
        this.m_value = temp1[1];
        this.persist = persist;
   }
    
    public DeviceTag(String namespace, 
               String name,
               String value,
               boolean persist) {
       m_namespace = namespace;
       m_name = name;
       m_value = value;
       this.persist = persist;
   }
   
    public DeviceTag(String namespace, 
               String name,
               String value) {
       m_namespace = namespace;
       m_name = name;
       m_value = value;
       this.persist = true;
   }
   
    /**
     * Namespace getter
     * @return String
     */
    @JsonProperty("name-space")
    public String getNamespace() {
        return m_namespace;
    }
    
    /**
     * Namespace setter
     * @param ns
     */
    public void setNamespace(String ns)
        throws TagInvalidNamespaceException {
        if (ns != null && 
                ns.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidNamespaceException(KEY_SEPARATOR + 
                    " is not allowed in the namespace, " + ns);
        }
        
        m_namespace = ns;
    }
    
    /**
     * Name getter
     * @return String
     */
    @JsonProperty("name")
    public String getName() {
        return m_name;
    }
    
    /**
     * Name setter
     * @param name
     */
    public void setName(String name) 
        throws TagInvalidNameException {
        if (name == null || 
                name.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidNameException("tag name can't be null or " +
                    KEY_SEPARATOR + " is not allowed in the tag name, " + name);
        }
        m_name = name;
    }
    
    /**
     * value getter
     * @return T
     */
    @JsonProperty("tag-value")
    public String getValue() {
        return m_value;
    }
    
    /**
     * Name setter
     * @param id
     */
    public void setValue(String value)
        throws TagInvalidValueException {
        if (value == null || 
                value.indexOf(KEY_SEPARATOR) != -1) {
            throw new TagInvalidValueException("tag value can't be null or " +
                    KEY_SEPARATOR + " is not allowed in the value, " + value);
        }
        m_value = value;
    }

    @JsonIgnore
    public String getDBKey() {
        return m_namespace + KEY_SEPARATOR +
            m_name + KEY_SEPARATOR + 
            m_value;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof DeviceTag))
            return false;
        DeviceTag other = (DeviceTag) obj;
        if (!m_namespace.equals(other.m_namespace))
            return false;
        if (!m_name.equals(other.m_name))
            return false;
        if (!m_value.equals(other.m_value))
            return false;
        return true;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     * 
     * The two tags are the same if they are in the same namespace and have the same id.
     */
    @Override
    public int hashCode() {
        final int prime = 7867;
        int result;
        result = m_namespace.hashCode();
        result = prime * result + m_name.hashCode();
        result = prime * result + m_value.hashCode();
        return result;
    }
    
    @Override
    public String toString() {
        return "Tag NS:" + m_namespace  + " name:" + m_name +
                " value:" + m_value;
    }

    @JsonProperty("persist")
    public boolean getPersist() {
        return persist;
    }
}
