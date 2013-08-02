/**
 *    Copyright 2013, Big Switch Networks, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.device.internal;

import java.util.Date;
import java.util.EnumSet;
import java.util.Formatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.device.IDeviceService;
import net.floodlightcontroller.device.IDeviceService.DeviceField;

/**
 * This is a thin wrapper around {@link Entity} that allows overriding
 * the behavior of {@link Object#hashCode()} and {@link Object#equals(Object)}
 * so that the keying behavior in a hash map can be changed dynamically
 * @author readams
 */
public class IndexedEntity {
    protected EnumSet<DeviceField> keyFields;
    protected Entity entity;
    private int hashCode = 0;
    protected static Logger logger =
            LoggerFactory.getLogger(IndexedEntity.class);
    
    /**
     * Create a new {@link IndexedEntity} for the given {@link Entity} using 
     * the provided key fields.
     * @param keyFields The key fields that will be used for computing
     * {@link IndexedEntity#hashCode()} and {@link IndexedEntity#equals(Object)}
     * @param entity the entity to wrap
     */
    public IndexedEntity(EnumSet<DeviceField> keyFields, Entity entity) {
        super();
        this.keyFields = keyFields;
        this.entity = entity;
    }

    public EnumSet<DeviceField> getKeyFields() {
        return keyFields;
    }

    public Entity getEntity() {
        return entity;
    }

    /**
     * Check whether this entity has non-null values in any of its key fields
     * @return true if any key fields have a non-null value
     */
    public boolean hasNonNullKeys() {
        for (DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    return true;
                case IPV4:
                    if (entity.ipv4Address != null) return true;
                    break;
                case SWITCH:
                    if (entity.switchDPID != null) return true;
                    break;
                case PORT:
                    if (entity.switchPort != null) return true;
                    break;
                case VLAN:
                    if (entity.vlan != null) return true;
                    break;
            }
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        
        if (hashCode != 0) {
            return hashCode;
        }

        final int prime = 31;
        hashCode = 1;
        for (DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    hashCode = prime * hashCode
                        + (int) (entity.macAddress ^ 
                                (entity.macAddress >>> 32));
                    break;
                case IPV4:
                    hashCode = prime * hashCode
                        + ((entity.ipv4Address == null) 
                            ? 0 
                            : entity.ipv4Address.hashCode());
                    break;
                case SWITCH:
                    hashCode = prime * hashCode
                        + ((entity.switchDPID == null) 
                            ? 0 
                            : entity.switchDPID.hashCode());
                    break;
                case PORT:
                    hashCode = prime * hashCode
                        + ((entity.switchPort == null) 
                            ? 0 
                            : entity.switchPort.hashCode());
                    break;
                case VLAN:
                    hashCode = prime * hashCode 
                        + ((entity.vlan == null) 
                            ? 0 
                            : entity.vlan.hashCode());
                    break;
            }
        }
        return hashCode;
    }
    
    @Override
    public boolean equals(Object obj) {
       if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        IndexedEntity other = (IndexedEntity) obj;
        
        if (!keyFields.equals(other.keyFields))
            return false;

        for (IDeviceService.DeviceField f : keyFields) {
            switch (f) {
                case MAC:
                    if (entity.macAddress != other.entity.macAddress)
                        return false;
                    break;
                case IPV4:
                    if (entity.ipv4Address == null) {
                        if (other.entity.ipv4Address != null) return false;
                    } else if (!entity.ipv4Address.
                            equals(other.entity.ipv4Address)) return false;
                    break;
                case SWITCH:
                    if (entity.switchDPID == null) {
                        if (other.entity.switchDPID != null) return false;
                    } else if (!entity.switchDPID.
                            equals(other.entity.switchDPID)) return false;
                    break;
                case PORT:
                    if (entity.switchPort == null) {
                        if (other.entity.switchPort != null) return false;
                    } else if (!entity.switchPort.
                            equals(other.entity.switchPort)) return false;
                    break;
                case VLAN:
                    if (entity.vlan == null) {
                        if (other.entity.vlan != null) return false;
                    } else if (!entity.vlan.
                            equals(other.entity.vlan)) return false;
                    break;
            }
        }
        
        return true;
    }

    public static String stringToHexString(String s) {
        StringBuilder sb = new StringBuilder(s.length() * 2);
        Formatter formatter = new Formatter(sb);
        for (int i = 0; i < s.length(); i++) {
            formatter.format("%02X", (int)s.charAt(i));
        }
        formatter.close();
        return sb.toString();
    }

    public static String hexStringToString(String s) throws Exception {
        if (s.length() % 2 != 0) {
            throw new Exception("Invalid Hex String: " + s);
        }
        StringBuilder sb = new StringBuilder(s.length() / 2);
        for( int i=0; i< s.length()-1; i+=2 ){
            String output = s.substring(i, (i + 2));
            int d = Integer.parseInt(output, 16);
            sb.append((char)d);
        }
        return sb.toString();
    }
    
    static final String INVALID_DPID_HEX = "FFFFFFFFFFFFFFFFFF";
    static final String INVALID_INTEGER_HEX = "FFFFFFFFFF";
    static final String INVALID_SHORT_HEX = "FFFFFFFF";
    
    public static String getKeyString(String entityClass, Entity entity, 
                                      EnumSet<DeviceField> keyFields) {
        StringBuilder sb = new StringBuilder();
        StringBuilder key = new StringBuilder();
        for (IDeviceService.DeviceField f : keyFields) {
            key.append(Integer.toHexString(f.ordinal()));
            switch (f) {
                case MAC:
                    if(sb.length() > 0) {
                        sb.append("-");
                    }
                    sb.append(String.format("%012X", entity.macAddress));
                    break;
                case IPV4:
                    if(sb.length() > 0) {
                        sb.append("-");
                    }
                    if (entity.ipv4Address != null) {
                        sb.append(String.format("%010X", entity.ipv4Address));
                    } else {
                        sb.append(INVALID_INTEGER_HEX);
                    }
                    break;
                case SWITCH:
                    if(sb.length() > 0) {
                        sb.append("-");
                    }
                    if (entity.switchDPID != null) {

                        sb.append(String.format("%018X", entity.switchDPID));
                    } else {
                        sb.append(INVALID_DPID_HEX);
                    }
                    break;
                case PORT:
                    if(sb.length() > 0) {
                        sb.append("-");
                    }
                    if (entity.switchPort != null) {
                        sb.append(String.format("%010X", entity.switchPort));
                    } else {
                        sb.append(INVALID_INTEGER_HEX);
                    }
                    break;
                case VLAN:
                    if(sb.length() > 0) {
                        sb.append("-");
                    }
                    if (entity.vlan != null) {
                        sb.append(String.format("%08X", entity.vlan));
                    } else {
                        sb.append(INVALID_SHORT_HEX);
                    }
                    break;
            }
        }
        return stringToHexString(entityClass) + "-" + key.toString() + "-" + sb.toString();
    }

    /**
     * Decodes a DeviceID into it's appropriate parts
     * @param keyString
     * @return
     * @throws Exception
     */
    public static EntityWithClassName getNamedEntityFromKeyString(String keyString) 
            throws Exception {
        String[] parts = keyString.split("-");
        if (parts.length < 2) {
            throw new Exception("Invalid device key string: " + keyString);
        }
        String hexClassName = parts[0];
        if (hexClassName == null || hexClassName.isEmpty()) {
            throw new Exception("Invlid key string, entity class name cannot be empty.");
        }
        String className = IndexedEntity.hexStringToString(hexClassName);
        char[] keys = parts[1].toCharArray();
        if (keys.length != parts.length -2) {
            throw new Exception("Invliad key string: " + keyString);
        }
        long mac = 0;
        Short vlan = null;
        Integer ipv4 = null;
        Long dpid = null;
        Integer port = null;
        EnumSet<DeviceField> keyFields = EnumSet.noneOf(DeviceField.class);
        for (int i = 0; i < keys.length; i++) {
            int vi = i + 2;
            DeviceField k = DeviceField.values()[Character.digit(keys[i], 16)];
            keyFields.add(k);
            switch (k) {
                case MAC:
                    mac = Long.valueOf(parts[vi], 16);
                    break;
                case IPV4:
                    if (!parts[vi].isEmpty() && !parts[vi].equals(INVALID_INTEGER_HEX)) {
                        ipv4 = Integer.valueOf(parts[vi], 16);
                    }
                    break;
                case SWITCH:
                    if (!parts[vi].isEmpty() && !parts[vi].equals(INVALID_DPID_HEX)) {
                        dpid = Long.valueOf(parts[vi], 16);
                    }
                    break;
                case PORT:
                    if (!parts[vi].isEmpty() && !parts[vi].equals(INVALID_INTEGER_HEX)) {
                        port = Integer.valueOf(parts[vi], 16);
                    }
                    break;
                case VLAN:
                    if (!parts[vi].isEmpty() && !parts[vi].equals(INVALID_SHORT_HEX)) {
                        vlan = Short.valueOf(parts[vi], 16);
                    }
                    break;
            }            
        }
        Entity e = new Entity(mac, vlan, ipv4, dpid, port, new Date());
        return new EntityWithClassName(className, keyFields, e);
    }
    
    public static class EntityWithClassName {
        public EntityWithClassName(String className, EnumSet<DeviceField> keyFields,
                                   Entity e) {
            this.entityClassName = className;
            this.keyFields = keyFields;
            this.entity = e;
        }
        String entityClassName;
        EnumSet<DeviceField> keyFields;
        Entity entity;
        
        public String getEntityClassName() {
            return entityClassName;
        }
        public void setEntityClassName(String entityClassName) {
            this.entityClassName = entityClassName;
        }
        public EnumSet<DeviceField> getKeyFields() {
            return keyFields;
        }
        public void setKeyFields(EnumSet<DeviceField> keyFields) {
            this.keyFields = keyFields;
        }
        public Entity getEntity() {
            return entity;
        }
        public void setEntity(Entity entity) {
            this.entity = entity;
        }
    }

    public static IndexedEntity getIndexedEntityFromKeyString(String keyString) 
            throws Exception {
        EntityWithClassName en = getNamedEntityFromKeyString(keyString);
        return new IndexedEntity(en.keyFields, en.entity);
    }
    public static Entity getEntityFromKeyString(String keyString) 
            throws Exception {
        return getNamedEntityFromKeyString(keyString).entity;
    }
}
