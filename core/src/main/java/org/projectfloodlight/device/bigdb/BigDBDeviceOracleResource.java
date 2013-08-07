package org.projectfloodlight.device.bigdb;

import java.util.ArrayList;

import org.openflow.util.HexString;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.device.IEntityClass;
import org.projectfloodlight.device.internal.Entity;
import org.projectfloodlight.device.internal.IndexedEntity;
import org.projectfloodlight.packet.IPv4;

public class BigDBDeviceOracleResource extends FloodlightResource {
    
    protected static final String CURRENT_STEP_NAME = "device-oracle";
    protected static final String ID_FIELD_NAME = "id";
    protected static final String MAC_FIELD_NAME = "mac";
    protected static final String VLAN_FIELD_NAME = "vlan";
    protected static final String ENTITY_CLASS_FIELD_NAME = "entity-class-name";
    protected static final String IP_ADDRESS_FIELD_NAME = "ip-address";
    protected static final String SWITCH_DPID_FIELD_NAME = "switch-dpid";
    protected static final String SWITCH_PORT_FIELD_NAME = "switch-port-number";

    public static class DeviceOracle {
        String deviceId;
        String mac;
        Short vlan;
        String entityClassName;
        String ipAddress;
        String switchDPID;
        Integer switchPortNumber;
        
        public DeviceOracle(String deviceId, String mac, Short vlan,
                            String entityClassName, String ipAddress, 
                            String switchDPID, Integer switchPortNumber) {
            this.deviceId = deviceId;
            this.mac = mac;
            this.vlan = vlan;
            this.entityClassName = entityClassName;
            this.ipAddress = ipAddress;
            this.switchDPID = switchDPID;
            this.switchPortNumber = switchPortNumber;
        }

        public DeviceOracle(String deviceId, long mac, Short vlan,
                            String entityClassName, Integer ipAddress, 
                            Long switchDPID, Integer switchPortNumber) 
            throws BigDBException {
            if (deviceId == null) {
                throw new BigDBException("Device ID cannot be null.");
            }
            String ip = 
                    ipAddress == null ? null : IPv4.fromIPv4Address(ipAddress);
            String dpid = switchDPID == null ? null : HexString.toHexString(switchDPID, 8);
            String macStr = HexString.toHexString(mac, 6);
            this.deviceId = deviceId;
            this.mac = macStr;
            this.vlan = vlan;
            this.entityClassName = entityClassName;
            this.ipAddress = ip;
            this.switchDPID = dpid;
            this.switchPortNumber = switchPortNumber;
        }

        @BigDBProperty(value = ID_FIELD_NAME)
        public String getDeviceId() {
            return deviceId;
        }
        public void setDeviceId(String deviceId) {
            this.deviceId = deviceId;
        }
        @BigDBProperty(value = MAC_FIELD_NAME)
        public String getMac() {
            return mac;
        }
        public void setMac(String mac) {
            this.mac = mac;
        }
        @BigDBProperty(value = VLAN_FIELD_NAME)        
        public Short getVlan() {
            return vlan;
        }
        public void setVlan(Short vlan) {
            this.vlan = vlan;
        }
        @BigDBProperty(value = ENTITY_CLASS_FIELD_NAME)
        public String getEntityClassName() {
            return entityClassName;
        }
        public void setEntityClassName(String entityClassName) {
            this.entityClassName = entityClassName;
        }
        @BigDBProperty(value = IP_ADDRESS_FIELD_NAME)
        public String getIpAddress() {
            return ipAddress;
        }
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }
        
        @BigDBProperty(value = SWITCH_DPID_FIELD_NAME)
        public String getSwitchDPID() {
            return switchDPID;
        }
        public void setSwitchDPID(String switchDPID) {
            this.switchDPID = switchDPID;
        }
        @BigDBProperty(value = SWITCH_PORT_FIELD_NAME)
        public Integer getSwitchPortNumber() {
            return switchPortNumber;
        }
        public void setSwitchPortNumber(Integer switchPortNumber) {
            this.switchPortNumber = switchPortNumber;
        }
    }

    public static DeviceOracle createDeviceOracle(String macStr, 
                                           Long vlan,
                                           String entityClassName, 
                                           String ipStr, 
                                           String dpidStr, 
                                           Short switchPortNumber) 
        throws BigDBException {
        if (macStr == null) {
            throw new BigDBException("Mac Address cannot be null.");
        }
        Short vlanShort = vlan == null ? null : Short.valueOf(vlan.shortValue());
        Integer port = switchPortNumber == null ? 
                           null : Integer.valueOf(switchPortNumber);
        Integer ipv4 = ipStr == null ? null : IPv4.toIPv4Address(ipStr);
        Long dpid = dpidStr == null ? null : HexString.toLong(dpidStr);
        IEntityClass entityClass = 
                getDeviceService().getEntityClassifier().
                    getEntityClassByName(entityClassName);
        if (entityClass == null) {
            throw new BigDBException("Invalid entity class name, config the " + 
                                     "entity class first: " + 
                                     entityClassName);
        }
        Entity en = new Entity(HexString.toLong(macStr), vlanShort,
                               ipv4, dpid, port, null);

        String deviceId = IndexedEntity.getKeyString(entityClassName, en, 
                                                entityClass.getKeyFields());
        return new DeviceOracle(deviceId, macStr, vlanShort, entityClassName, 
                                ipStr, dpidStr, port);
    }   
    @BigDBQuery
    public static Object getDeviceOracle(
            @BigDBParam("query") Query query) throws BigDBException {
        String entityClassName = null;
        
        assert query.getSteps() != null;
        assert query.getSteps().size() == 1;
        assert query.getStep(0).getName().equals(CURRENT_STEP_NAME);
        
        Step currentStep = query.getStep(0);
        
        final String id = currentStep.getExactMatchPredicateString(ID_FIELD_NAME);
        String macStr = currentStep.getExactMatchPredicateString(MAC_FIELD_NAME);
        Long vlanObj = (Long)currentStep.getExactMatchPredicateValue(VLAN_FIELD_NAME);
        entityClassName = currentStep.getExactMatchPredicateString(ENTITY_CLASS_FIELD_NAME);
        String dpidStr = currentStep.getExactMatchPredicateString(SWITCH_DPID_FIELD_NAME);
        Short portShort = (Short)currentStep.getExactMatchPredicateValue(SWITCH_PORT_FIELD_NAME);
        String ipStr = currentStep.getExactMatchPredicateString(IP_ADDRESS_FIELD_NAME);

        if (id != null && 
            (macStr != null || vlanObj != null || entityClassName != null)) {
            throw new BigDBException("This query only accepts a device id or " +
                                     "a combination of mac, vlan and entity-class-name, " +
                                     "but not both.");
        } if (id != null) {
            // convert from device-id to individual components
            try {
                IndexedEntity.EntityWithClassName e = 
                        IndexedEntity.getNamedEntityFromKeyString(id);
                DeviceOracle d = new DeviceOracle(id, e.getEntity().getMacAddress(), 
                                                  e.getEntity().getVlan(),
                                                  e.getEntityClassName(),
                                                  e.getEntity().getIpv4Address(), 
                                                  e.getEntity().getSwitchDPID(), 
                                                  e.getEntity().getSwitchPort());
                ArrayList<DeviceOracle> dd = new ArrayList<DeviceOracle>();
                dd.add(d);
                return dd;
            } catch (Exception e) {
                throw new BigDBException("Invalid device id " + id);
            }
        } else if (macStr != null && entityClassName != null) {
            DeviceOracle d = createDeviceOracle(macStr, vlanObj, entityClassName,
                                              ipStr, dpidStr, portShort);

            ArrayList<DeviceOracle> dd = new ArrayList<DeviceOracle>();
            dd.add(d);
            return dd;
        } else {
            throw new BigDBException("This query requires a device id or a " +
                    "combination of mac, vlan and entity-class-name");
        }
    }
}
