package net.floodlightcontroller.device.internal;


import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.bigdb.BigDBException;
import net.bigdb.auth.AuthContext;
import net.bigdb.data.DataNode;
import net.bigdb.data.DataNodeSet;
import net.bigdb.data.MutationListener;
import net.bigdb.data.ServerDataSource;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.bigdb.service.Treespace;
import net.bigdb.util.Path;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.device.IDevice;
import net.floodlightcontroller.device.IEntityClass;
import net.floodlightcontroller.device.IEntityClassifierService;
import net.floodlightcontroller.device.SwitchInterface;
import net.floodlightcontroller.device.SwitchPort;
import net.floodlightcontroller.device.internal.IndexedEntity.EntityWithClassName;
import net.floodlightcontroller.device.tag.DeviceTag;
import net.floodlightcontroller.device.tag.DeviceTagResource;
import net.floodlightcontroller.device.tag.IDeviceTagListener;
import net.floodlightcontroller.device.tag.IDeviceTagService;
import net.floodlightcontroller.device.tag.SwitchInterfaceRegexMatcher;
import net.floodlightcontroller.device.tag.TagDoesNotExistException;
import net.floodlightcontroller.device.tag.TagInvalidHostMacException;
import net.floodlightcontroller.device.tag.TagManagerNotification;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@LogMessageCategory("Device Management")
public class TaggingDeviceManagerImpl extends DeviceManagerImpl
    implements IFloodlightModule, IDeviceTagService, MutationListener {
    protected final static Logger logger =
            LoggerFactory.getLogger(TaggingDeviceManagerImpl.class);

    // Additional dependencies
    protected IEntityClassifierService ecs;

    protected static final String TAG_MANAGER_BIGDB_PATH = "/tag-manager/tag-entity";
    protected static final String TAG_MANGER_MAPPING_BIGDB_PATH = "/tag-manager/tag-entity/mapping";
    protected static final String TAG_NAMESPACE_LEAF = "name-space";
    protected static final String TAG_NAME_LEAF = "name";
    protected static final String TAG_VALUE_LEAF = "tag-value";
    protected static final String TAG_PERSIST_LEAF = "persist";
    protected static final String ENTITY_CONFIG_LIST_MAPPING = "mapping";
    protected static final String ENTITY_CONFIG_MAC_LEAF = "mac";
    protected static final String ENTITY_CONFIG_DPID_LEAF = "dpid";
    protected static final String ENTITY_CONFIG_VLAN_LEAF = "vlan";
    protected static final String ENTITY_CONFIG_INTERFACE_LEAF = "interface-name";
    /*
     * spoofing protection tables and columns
     */
    protected final static String HOST_SECURITY_AP_BIGDB_PATH = "/core/device/security-attachment-point";
    protected final static String HOST_SECURITY_IP_BIGDB_PATH = "/core/device/security-ip-address";

    public static class DeviceId {
        String addressSpace;
        Short vlan;
        Long mac;
        public DeviceId(String addressSpace, Short vlan, Long mac) {
            super();
            this.addressSpace = addressSpace;
            this.vlan = vlan;
            this.mac = mac;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result =
                    prime
                            * result
                            + ((addressSpace == null)
                                    ? 0
                                    : addressSpace.hashCode());
            result = prime * result + ((mac == null) ? 0 : mac.hashCode());
            result = prime * result + ((vlan == null) ? 0 : vlan.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            DeviceId other = (DeviceId) obj;
            if (addressSpace == null) {
                if (other.addressSpace != null) return false;
            } else if (!addressSpace.equals(other.addressSpace))
                                                                return false;
            if (mac == null) {
                if (other.mac != null) return false;
            } else if (!mac.equals(other.mac)) return false;
            if (vlan == null) {
                if (other.vlan != null) return false;
            } else if (!vlan.equals(other.vlan)) return false;
            return true;
        }
        @Override
        public String toString() {
            return "DeviceId [addressSpace=" + addressSpace + ", vlan="
                    + vlan + ", mac=" + mac + "]";
        }
    }

    public static class ScopedIp {
        public String addressSpace;
        public Integer ip;
        public ScopedIp(String addressSpace, Integer ip) {
            super();
            this.addressSpace = addressSpace;
            this.ip = ip;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result =
                    prime
                            * result
                            + ((addressSpace == null)
                                    ? 0
                                    : addressSpace.hashCode());
            result = prime * result + ((ip == null) ? 0 : ip.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            ScopedIp other = (ScopedIp) obj;
            if (addressSpace == null) {
                if (other.addressSpace != null) return false;
            } else if (!addressSpace.equals(other.addressSpace))
                return false;
            if (ip == null) {
                if (other.ip != null) return false;
            } else if (!ip.equals(other.ip)) return false;
            return true;
        }
        @Override
        public String toString() {
            return "ScopedIp [addressSpace=" + addressSpace + ", ip=" + ip
                    + "]";
        }
    }

    /********
     * Tag States and lock
     */
    protected ReentrantReadWriteLock m_lock;

    /**
     * m_tags is a map with tag-namespace as key and entry is a map with tag-name
     * as key and entry is set of Tags that have that tag-namespace and tag-name
     * and all values for tag-value. This Map of Maps serves as a dictionary
     * of tags vocabulary indexed by tag-namespace and for a given tag-namespace,
     * the entry is all tags - {tag-namespace, tag-name, tag-value} indexed by
     * tag-name.
     */
    protected Map<String, Map<String, Set<DeviceTag>>> m_tags;

    /**
     * tagToEntities is a map keyed by tag. This map's entry is set of
     * all tags - EntityConfig instances. This map serves as a dictionary of
     * all tag-mappings indexed by tag as it appears in form of compound key in
     * DB.
     */
    protected Map<String, Set<EntityConfig>> tagToEntities;

    /**
     * entityToTags is a map keyed by EntityConfig and Entry is set of all
     * tags - {tag-namespace, tag-name, tag-value}. This map serves as a
     * dictionary of all tag-mappings indexed by EntityConfig.
     */
    private Map<EntityConfig, Set<DeviceTag>> entityToTags;

    /********
     * Tag notification callbacks registered.
     */
    private Set<IDeviceTagListener> m_listeners;

    /*
     * Spoofing protection
     */

    /**
     * We synchronize on this object when we change our internal anti-spoofing
     * structures. We don't need to synchronize on reads since we use
     * ConcurrentHashMaps. Of course, we could also update our data structures
     * lock-free but updating is not on the critical path so the coding and
     * complexity overhead isn't worth it.
     */
    protected Object antiSpoofingConfigWriteLock;

    protected SwitchInterfaceRegexMatcher interfaceRegexMatcher;

    /**
     * TODO: These data structures use our bigcon syntax of creating strings
     * with empty values. We should NOT be doing this, we need to refactor
     * these maps to use proper datastructures.
     */

    /**
     * Anti-spoofing configuration: per IP-address (scoped to an address-space):
     * the set of devices/hosts that are allowed to use this IP
     */
    protected ConcurrentHashMap<ScopedIp,Collection<DeviceId>> hostSecurityIpMap;

    /**
     * NOTE: CURRENTLY UNUSED SINCE WE FUNNEL EVERYTHING THROUGH REGEX
     * MATCHING.
     * Anti-spoofing configuration: per address-space:
     * the mapping from MAC address to switch port.
     * First key: address space name
     * Second key: Mac address
     * Data: collection of switch-port names.
     */
    protected ConcurrentHashMap<String,
            ConcurrentHashMap<Long,
            Collection<SwitchInterface>>> hostSecurityInterfaceMap;

    /**
     * Anti-spoofing configuration: the mapping from host (deviceId)
     * to the key (tag?) that interfaceRegexMatcher will use when querying for
     * switch interfaces matching this host
     * Key: deviceId
     * Data: collection of keys to query
     */
    protected ConcurrentHashMap<DeviceId, Collection<String>>
            hostSecurityInterfaceRegexMap;

    @Override
    protected Device learnDeviceByEntity(Entity entity) {
        return super.learnDeviceByEntity(entity);
    }

    /**
     * Clears the in-memory tags
     */
    protected void clearTagsFromMemory() {
        acquireTagWriteLock();
        this.m_tags.clear();
        this.tagToEntities.clear();
        this.entityToTags.clear();
        this.m_lock.writeLock().unlock();
    }

    //***************
    // ITagManagerService
    //***************

    /**
     * Create a new tag.
     * @param tag
     */
    @Override
    public DeviceTag createTag(String ns, String name, String value) {
        return new DeviceTag(ns, name, value, true);
    }

    /**
     * Create a new tag, with persist.
     * @param tag
     */
    @Override
    public DeviceTag createTag(String ns, String name, String value,
                         boolean persist) {
        if (ns.equals(DataNode.DEFAULT_STRING))
            ns = null;
        if (name.equals(DataNode.DEFAULT_STRING))
            name = null;
        if (value.equals(DataNode.DEFAULT_STRING))
            value = null;
        return new DeviceTag(ns, name, value, persist);
    }

    private Query getTagQuery(DeviceTag tag) throws BigDBException {
        Query query =  Query.builder().setBasePath(String.format(
                "%s[%s=$tagname][%s=$tagnamespace][%s=$tagvalue]",
                TAG_MANAGER_BIGDB_PATH, TAG_NAME_LEAF, TAG_NAMESPACE_LEAF, TAG_VALUE_LEAF))
                .setVariable("tagname", tag.getName())
                .setVariable("tagnamespace", tag.getNamespace())
                .setVariable("tagvalue", tag.getValue())
                .getQuery();
        return query;
    }
    /**
     * Add a new tag. The tag is saved in DB.
     * @param tag
     */
    @Override
    public void addTag(DeviceTag tag) {
        if (tag == null) return;

        ObjectMapper mapper = new ObjectMapper();
        try {
            String tagJsonString = mapper.writeValueAsString(tag);
            InputStream inputStream =
                    new ByteArrayInputStream(tagJsonString.getBytes("UTF-8"));
            Treespace t = bigDb.getControllerTreespace();
            t.replaceData(getTagQuery(tag), Treespace.DataFormat.JSON, inputStream, AuthContext.SYSTEM);
        } catch (Exception e) {
            logger.error("Error adding Tag=[{}], Exception: {}", tag, e.getMessage());
        }
    }

    /**
     * Delete a tag. The tag is removed from DB.
     * @param tag
     */
    @Override
    public void deleteTag(DeviceTag tag) throws TagDoesNotExistException {
        if (tag == null) return;

        String ns = tag.getNamespace();
        if (m_tags.get(ns) == null || !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }

        try {
            Treespace t = bigDb.getControllerTreespace();
            t.deleteData(getTagQuery(tag), AuthContext.SYSTEM);
        } catch (BigDBException e) {
            logger.error("Error deleting tag {}, exception {}", tag.toString(), e.getMessage());
        }
    }

    /**
     * Map a tag to a host. The mapping is saved in DB.
     * @param tag
     * @param hostmac
     */
    @Override
    public void mapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid,
                             String interfaceName)
        throws TagDoesNotExistException, TagInvalidHostMacException {
        if (tag == null) throw new TagDoesNotExistException("null tag");
        if (hostmac != null && !Ethernet.isMACAddress(hostmac)) {
            throw new TagInvalidHostMacException(hostmac + " is an invalid mac address");
        }

        String ns = tag.getNamespace();
        if (m_tags.get(ns) == null ||
            !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }

        if (hostmac == null && vlan == null && dpid == null && interfaceName == null)
            return;

        String vlanStr = null;
        if (vlan != null) {
            vlanStr = vlan.toString();
        }
        if (dpid == null && interfaceName != null)
            return;

        EntityConfig e = new EntityConfig(hostmac, vlanStr, dpid, interfaceName);
        try {
            addTagMappingToBigDB(tag, e);
        } catch (BigDBException exc) {
            logger.error("Error adding tag mapping to BigDB, {}", exc.getMessage());
        }
    }

    /**
     * UnMap a tag from a host. The mapping is removed from DB.
     * @param tag
     * @param hostmac
     */
    @Override
    public void unmapTagToHost(DeviceTag tag, String hostmac, Short vlan, String dpid,
                               String interfaceName)
        throws TagDoesNotExistException, TagInvalidHostMacException {
        if (tag == null) throw new TagDoesNotExistException("null tag");
        if (hostmac != null && !Ethernet.isMACAddress(hostmac)) {
            throw new TagInvalidHostMacException(hostmac +
                                                 " is an invalid mac address");
        }
        if (hostmac == null && vlan == null && dpid == null &&
                interfaceName == null)
            return;
        String blankStr = "";
        String vlanStr = blankStr;
        if (vlan != null)
            vlanStr = vlan.toString();

        if (hostmac == null)
            hostmac = blankStr;

        if (dpid == null && interfaceName != null)
            return;

        if (dpid == null)
            dpid = blankStr;
        if (interfaceName == null)
            interfaceName = blankStr;

        String ns = tag.getNamespace();

        if (m_tags.get(ns) == null ||
            !m_tags.get(ns).containsKey(tag.getName())) {
            throw new TagDoesNotExistException(tag.toString());
        }

        EntityConfig e = new EntityConfig(hostmac, vlanStr, dpid, interfaceName);
        try {
            deleteTagMappingToBigDB(tag, e);
        } catch (BigDBException exc) {
            logger.error("Error deleting tag mapping from BigDB, {}", exc.getMessage());
        }
    }

    @Override
    public Set<DeviceTag> getTags(String ns, String name) {
        if (ns != null && ns.equals("")) {
            ns = "default";
        }

        Map<String, Set<DeviceTag>> nsTags = m_tags.get(ns);
        if (nsTags != null) {
            return nsTags.get(name);
        }
        return null;
    }

    /**
     * An unmodifiable set of all the tags.
     * @return An unmodifiable set of all the tags.
     */
    protected Set<DeviceTag> getTags() {
        Set<DeviceTag> tSet = new HashSet<DeviceTag>();
        for (Map<String, Set<DeviceTag>> tMap : m_tags.values()) {
            for (Set<DeviceTag> ts : tMap.values()) {
                tSet.addAll(ts);
            }
        }
        return Collections.unmodifiableSet(tSet);
    }

    @Override
    public Set<DeviceTag> getTagsByNamespace(String ns) {
        if (ns == null) return null;

        Set<DeviceTag> tags = new HashSet<DeviceTag>();

        Map<String, Set<DeviceTag>> nsTags = m_tags.get(ns);
        if (nsTags != null) {
            Iterator<Map.Entry<String, Set<DeviceTag>>> it = nsTags.entrySet().iterator();
            while (it.hasNext()) {
                tags.addAll(it.next().getValue());
            }
            return tags;
        } else {
            return null;
        }
    }

    @Override
    public void addListener(IDeviceTagListener listener) {
        m_listeners.add(listener);
    }

    @Override
    public void removeListener(IDeviceTagListener listener) {
        m_listeners.remove(listener);
    }

    @Override
    public Set<DeviceTag> getTagsByDevice(IDevice device) {
        if (logger.isDebugEnabled()) {
            logger.debug("Getting tags for device-" + device);
        }

        Set <DeviceTag> tagsForEntities = new HashSet<DeviceTag>();
        Set <Entity> allPartialEntities = new HashSet <Entity>();
        allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                          null, null, null, null));
        for (Short vlan : device.getVlanId()) {
            allPartialEntities.add(new Entity(0, vlan, null, null,
                                              null, null));
            allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, null, null, null));
            for (SwitchPort switchPort : device.getAttachmentPoints(true)) {
                allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, switchPort.getSwitchDPID(),
                                              null, null));
                allPartialEntities.add(new Entity(device.getMACAddress(), vlan,
                                              null, switchPort.getSwitchDPID(),
                                              switchPort.getPort(), null));
                allPartialEntities.add(new Entity(0, vlan, null,
                                                  switchPort.getSwitchDPID(),
                                                  null, null));
                allPartialEntities.add(new Entity(0, vlan, null,
                                                  switchPort.getSwitchDPID(),
                                                  switchPort.getPort(), null));
            }
        }
        for (SwitchPort switchPort : device.getAttachmentPoints(true)) {
            allPartialEntities.add(new Entity(0, null, null,
                                      switchPort.getSwitchDPID(), null, null));
            allPartialEntities.add(new Entity(0, null, null,
                                   switchPort.getSwitchDPID(),
                                   switchPort.getPort(), null));
            allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                              null, switchPort.getSwitchDPID(),
                                              null, null));
            allPartialEntities.add(new Entity(device.getMACAddress(), null,
                                              null, switchPort.getSwitchDPID(),
                                              switchPort.getPort(), null));

        }
        for (Entity thisEntity : allPartialEntities) {
            tagsForEntities.addAll(this.getTagsByEntityConfig(
                                   EntityConfig.convertEntityToEntityConfig(
                                            floodlightProvider, thisEntity)));
        }
        return tagsForEntities;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#
     * getTagsByHost(java.lang.String, java.lang.Short, java.lang.String,
     * java.lang.String)
     */
    @Override
    public Set<DeviceTag> getTagsByHost(String hostmac, Short vlan, String dpid,
                                  String interfaceName) {
        if (hostmac == null && vlan == null && dpid == null && interfaceName == null)
            return new HashSet<DeviceTag>();
        String vlanStr = null;
        if (vlan != null)
            vlanStr = vlan.toString();
        Set<DeviceTag> tags = new HashSet<DeviceTag>();
        tags.addAll(this.getTagsByEntityConfig(
              new EntityConfig(hostmac, vlanStr, dpid, interfaceName)));
        return tags;
    }

    /* (non-Javadoc)
     * @see com.bigswitch.floodlight.tagmanager.ITagManagerService#
     * getDevicesByTag(com.bigswitch.floodlight.tagmanager.Tag)
     */
    @Override
    public Set <IDevice> getDevicesByTag(String tag) {
        Set<EntityConfig> entities = this.tagToEntities.get(tag);
        if (entities == null)
            return null;
        Set <IDevice> retDevices= new HashSet <IDevice>();
        for (EntityConfig entity : entities) {
            Short vlan = (entity.vlan == null ? null : new Short(entity.vlan));
            Long dpid = (entity.dpid == null ? null : HexString.toLong(entity.dpid));
            Iterator<? extends IDevice> devicesReMapped =
                    this.queryDevices(HexString.toLong(entity.mac),
                                      vlan,
                                      null,
                                      dpid,
                                      extractSwitchPortNumber(entity.dpid,
                                              entity.interfaceName));
            while (devicesReMapped.hasNext()) {
                retDevices.add(devicesReMapped.next());
            }
        }
        return retDevices;
    }

    public void removeAllListeners() {
        m_listeners.clear();
    }

    //****************************
    // Internal Methods = Tag related
    //*****************************

    private Integer extractSwitchPortNumber(String dpid, String ifaceName) {
        if (dpid != null && ifaceName != null) {
            IOFSwitch sw = floodlightProvider.getSwitch(HexString.toLong(dpid));
            if (sw == null) {
                logger.info("Switch {} not in switch map", dpid);
                return null;
            }
            ImmutablePort p = sw.getPort(ifaceName);
            if (p == null) {
                logger.info("On Switch {} Port {} does not exist yet",
                             dpid, ifaceName);
                return null;
            }
            return (int)p.getPortNumber();
        }
        return null;
    }

    /**
     * finds the change set of devices that got remapped and then notify
     * @param thisEntity
     */
    private void notifyAllDevicesReMapped(EntityConfig thisEntity) {
        /* query for all devices that match this entity and then
         * notify the tag listeners of this change
         */
        Integer port = null;
        if (thisEntity.isDpidValid() && thisEntity.isInterfaceNameValid()) {
            port = extractSwitchPortNumber(thisEntity.dpid,
                                           thisEntity.interfaceName);
        }

        Long longDpid = null;
        if (thisEntity.isDpidValid())
            longDpid = Long.valueOf(HexString.toLong(thisEntity.dpid));
        Long longMac = null;
        if (thisEntity.isMacValid())
            longMac = Long.valueOf(HexString.toLong(thisEntity.mac));
        Short vlan = null;
        if (thisEntity.isVlanValid())
            vlan = new Short(thisEntity.vlan);
        Iterator<Device> devicesReMapped =
                this.getDeviceIteratorForQuery(longMac, vlan, null, longDpid,
                                               port);
        ArrayList <Device> devicesToNotify = new ArrayList <Device>();
        while (devicesReMapped.hasNext()) {
            Device device = devicesReMapped.next();
            if (reclassifyDevice(device) == false) {
                devicesToNotify.add(device);
            }
        }
        if (devicesToNotify.isEmpty())
            return;
        Iterator<? extends IDevice> devicesReMappedToNotify =
                devicesToNotify.iterator();
        TagManagerNotification notification =
                new TagManagerNotification(devicesReMappedToNotify);
        notification.setAction(
                           TagManagerNotification.Action.TAGDEVICES_REMAPPED);
        this.notifyListeners(notification);
    }

    private void addEntityTagConfig(EntityConfig thisEntity, DeviceTag thisTag) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding entity mapping, EntityConfig=[{}], Tag=[{}]", thisEntity, thisTag);
        }
        Set <DeviceTag> thisEntityTags = entityToTags.get(thisEntity);
        if (thisEntityTags == null) {
            thisEntityTags = new HashSet <DeviceTag>();
            entityToTags.put(thisEntity, thisEntityTags);
        }
        if (thisEntityTags.add(thisTag) == true) {
            /* notify listeners of this change
             *
             */
            notifyAllDevicesReMapped(thisEntity);
        }

        Set <EntityConfig> thisTagEntities = 
                tagToEntities.get(thisTag.getDBKey());
        if (thisTagEntities == null) {
            thisTagEntities = new HashSet<EntityConfig>();
        }
        tagToEntities.put(thisTag.getDBKey(), thisTagEntities);
        thisTagEntities.add(thisEntity);
    }

    private void removeEntityTagConfig(EntityConfig thisEntity, DeviceTag thisTag) {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing entity mapping, EntityConfig=[{}], Tag=[{}]", thisEntity, thisTag);
        }
        Set <DeviceTag> thisEntityTags = entityToTags.get(thisEntity);
        if (thisEntityTags != null) {
            if (thisEntityTags.contains(thisTag)) {
                if (thisEntityTags.remove(thisTag) == true) {
                    notifyAllDevicesReMapped(thisEntity);
                }
            }
            if (thisEntityTags.isEmpty()) {
                entityToTags.remove(thisEntity);
            }
        }

        Set <EntityConfig> thisTagEntities = 
                tagToEntities.get(thisTag.getDBKey());
        if (thisTagEntities != null) {
            thisTagEntities.remove(thisEntity);
            if (thisTagEntities.isEmpty()) {
                tagToEntities.remove(thisTag.getDBKey());
            }
        }
    }

    protected Set<DeviceTag> getTagsByEntityConfig(EntityConfig thisEntity) {
        if (thisEntity == null)
            return new HashSet<DeviceTag>();
        if (logger.isTraceEnabled()) {
            logger.trace("get Tags for entity - " + thisEntity.toString());
            for (EntityConfig entity: this.entityToTags.keySet()) {
                logger.trace("Found a entityConfig key in entityToTags - " +
                              entity.toString());
            }
        }
        Set<DeviceTag> tags = this.entityToTags.get(thisEntity);

        if (tags == null) {
            return new HashSet<DeviceTag>();
        }
        if (logger.isDebugEnabled()) {
            for (DeviceTag tag : tags) {
                logger.debug("getTagsByEntityConfig: Tag value is - " + tag);
            }
        }
        return tags;
    }

    protected Set<EntityConfig> getEntityConfigsByTag(DeviceTag tag) {
        if (tag == null)
            return null;
        Set<EntityConfig> entities = this.tagToEntities.get(tag.getDBKey());
        if (entities == null) {
            return new HashSet<EntityConfig>();
        }
        return entities;
    }

    @LogMessageDoc(level="ERROR",
            message="Exception caught handling tagManager notification",
            explanation="A transient error occurred while notifying tog changes",
            recommendation=LogMessageDoc.TRANSIENT_CONDITION)
    @SuppressWarnings("incomplete-switch")
    private void notifyListeners(TagManagerNotification notification) {
        if (m_listeners != null) {
            for (IDeviceTagListener listener : m_listeners) {
                try {
                    switch (notification.getAction()) {
                        case ADD_TAG:
                            listener.tagAdded(notification.getTag());
                            break;
                        case DELETE_TAG:
                            listener.tagDeleted(notification.getTag());
                            break;
                        case TAGDEVICES_REMAPPED:
                            logger.debug("Notifying listeners that the tags of"
                                         + " devices for remapped");
                            listener.tagDevicesReMapped(
                                                    notification.getDevices());
                            break;
                    }
                }
                catch (Exception e) {
                    logger.error("Exception caught handling tagManager notification", e);
                }
            }
        }
    }

    /**
     * Delete a new tag without updating storage
     * @param tag
     */
    private boolean deleteTagInternal(DeviceTag tag) {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing tag {}", tag);
        }
        boolean retCode = true;

        String ns = tag.getNamespace();
        Map<String, Set<DeviceTag>> nsTags = m_tags.get(ns);

        if (nsTags != null) {
            Set<DeviceTag> tags = nsTags.get(tag.getName());
            if (tags == null) {
                retCode = false;
            } else {
                retCode = tags.remove(tag);
                if (tags.size() == 0) nsTags.remove(tag.getName());
                if (nsTags.size() == 0) m_tags.remove(ns);
            }
        } else {
            retCode = false;
        }
        // Remove tag mapping when the tag is removed
        if (retCode) {
            Set<EntityConfig> entities = 
                    this.tagToEntities.remove(tag.getDBKey());
            if (entities != null) {
                for (EntityConfig thisEntity : entities) {
                    this.removeEntityTagConfig(thisEntity, tag);
                }
            }
        }

        return retCode;
    }

    /**
     * Inserts a new tag and notifies listeners.
     * @param newTag The tag to insert.
     */
    private void insertTag(DeviceTag tag) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding new Tag=[{}]", tag);
        }
        boolean shouldNotify = true;
        String ns = tag.getNamespace();
        Map<String, Set<DeviceTag>> nsTags = m_tags.get(ns);
        if (nsTags == null) {
            nsTags = new ConcurrentHashMap<String, Set<DeviceTag>>();
            m_tags.put(ns, nsTags);
        }
        Set<DeviceTag> tags = nsTags.get(tag.getName());
        if (tags == null) {
            tags = new CopyOnWriteArraySet<DeviceTag>();
            nsTags.put(tag.getName(), tags);
        }
        shouldNotify = tags.add(tag);

        if (shouldNotify) {
            TagManagerNotification.Action action = TagManagerNotification.Action.ADD_TAG;
            TagManagerNotification notification = new TagManagerNotification(tag, null, action);
            notifyListeners(notification);
        }
    }

    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Cannot specify a VLAN for " +
                        "HostSecurityAttachmentPoint if the address space is " +
                        "default.",
                explanation="Unsupported Vlan in a host security Ip-Address " +
                        "configuration for default address space",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="ERROR",
                message="Invalid MAC in from " +
                        "HostSecurityAttachmentPoint table",
                explanation="Invalid MAC in a host security IP-Address "
                                + "configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION),
         @LogMessageDoc(level="ERROR",
                message="Invalid VLAN in from " +
                        "HostSecurityAttachmentPoint table",
                explanation="Invalid Vlan in a host security Ip-Address "
                                + "configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION),
        @LogMessageDoc(level="ERROR",
                message="Invalid compound primary key in " +
                         "HostSecurityAttachmentPoint table",
                explanation="Invalid compound primary key in a host " +
                            "security attachment point configuration",
                recommendation=LogMessageDoc.GENERIC_ACTION)
    })
    private boolean checkFieldsForAntiSpoofing(DeviceId d, Integer ip) {
        String addrSpace = d.addressSpace;
        Short vlan = d.vlan;
        Long mac = d.mac;

        if ((vlan != null) && (!addrSpace.equals("default"))) {
            logger.error("Error, can not specify a VLAN={} if address space is not default, address space={}",
                         vlan.toString(), addrSpace);
            return false;
        }

        if ((vlan != null) && (vlan < 1 || vlan > 4095)) {
            logger.error("Invalid VLAN={} for address space={}, MAC={}, IP={}",
                         new Object[] {vlan, HexString.toHexString(mac),
                                       ip != null ? IPv4.toIPv4AddressBytes(ip) : "null"});
            return false;
        }

        if ((mac != null) && (mac >= (1L<<48))) {
            logger.error("Invalid MAC={} for address space={}, VLAN={}, IP={}",
                         new Object[] {mac, addrSpace, vlan,
                                       ip != null ? IPv4.toIPv4AddressBytes(ip) : "null"});
            return false;
        }

        return true;
    }

    /**
     * Add an IP to MAC anti-spoofing entry. Lock MAC to IP
     *
     * NOTE: The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param ip
     */
    private void addAntiSpoofingIp2Mac(DeviceId d, Integer ip) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding anti spoofing IP to MAC, address space=[{}], VLAN=[{}], MAC=[{}], IP=[{}]",
                         new Object[] {d.addressSpace, d.vlan, HexString.toHexString(d.mac), IPv4.fromIPv4Address(ip)});
        }

        if (!checkFieldsForAntiSpoofing(d, ip)) {
            return;
        }

        ScopedIp scopedIp = new ScopedIp(d.addressSpace, ip);
        Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);
        if (hosts == null) {
            hosts = Collections.newSetFromMap(new ConcurrentHashMap<DeviceId, Boolean>());
        }
        hosts.add(d);
        hostSecurityIpMap.putIfAbsent(scopedIp, hosts);
    }

    /**
     * Remove an IP to MAC anti-spoofing entry.
     *
     * NOTE: The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param ip
     */
    private void removeAntiSpoofingIp2Mac(DeviceId d, Integer ip) {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing anti spoofing IP to MAC, address space=[{}], VLAN=[{}], MAC=[{}], IP=[{}]",
                         new Object[] {d.addressSpace, d.vlan, HexString.toHexString(d.mac), IPv4.fromIPv4Address(ip)});
        }

        if (!checkFieldsForAntiSpoofing(d, ip)) {
            return;
        }

        ScopedIp scopedIp = new ScopedIp(d.addressSpace, ip);

        Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);
        if (hosts == null) {
            return;
        }

        hosts.remove(d);

        if (hosts.isEmpty())
            hostSecurityIpMap.remove(scopedIp);
    }

    /**
     * Add an host to SwitchInterface anti-spoofing entry.
     *
     * The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param dpid
     * @param ifaceName
     */
    private void addHostSecurityInterfaceEntry(DeviceId d, String dpid, String ifaceName) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding security interface, Device=[{}], Switch=[{}], Intf=[{}]",
                         new Object[] {d, dpid, ifaceName});
        }

        if (!checkFieldsForAntiSpoofing(d, null)) {
            return;
        }

        Collection<String> keys = hostSecurityInterfaceRegexMap.get(d);

        if (keys == null) {
            keys = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
        }
        Long lDpid = null;
        if (!dpid.equals(DataNode.DEFAULT_STRING)) {
            lDpid = HexString.toLong(dpid);
        }

        // TODO - refactor this to not use | but a real key (i.e. not a String)
        String key = d.addressSpace + "|" + d.vlan + "|" + d.mac + "|"
                    + dpid + "|" + ifaceName;
        keys.add(key);
        hostSecurityInterfaceRegexMap.put(d, keys);
        interfaceRegexMatcher.addOrUpdate(key, lDpid, ifaceName);

        /* CURRENTLY UNUSED:
         * use for exact matches
        SwitchInterface switchIface = new SwitchInterface(dpid, ifaceName);
        ConcurrentHashMap<Long, Collection<SwitchInterface>> mac2switchIface =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2switchIface == null) {
            mac2switchIface = new ConcurrentHashMap<Long,
                                                    Collection<SwitchInterface>>();
        }
        Collection<SwitchInterface> ifaces = mac2switchIface.get(mac);
        if (ifaces == null) {
            ifaces = Collections.newSetFromMap(
                            new ConcurrentHashMap<SwitchInterface, Boolean>());
        }
        ifaces.add(switchIface);
        mac2switchIface.putIfAbsent(mac, ifaces);
        hostSecurityInterfaceMap.putIfAbsent(addrSpace, mac2switchIface);
        */
    }

    /**
     * Remove an host to SwitchInterface anti-spoofing entry.
     *
     * The caller needs to hold the anti-spoofing write lock.
     * @param addrSpace
     * @param vlan
     * @param mac
     * @param dpid
     * @param ifaceName
     */
    protected void removeHostSecurityInterfaceEntry(DeviceId d, String dpid, String ifaceName) {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing security interface, Device=[{}], Switch=[{}], Intf=[{}]",
                         new Object[] {d, dpid, ifaceName});
        }

        if (!checkFieldsForAntiSpoofing(d, null)) {
            return;
        }

        Collection<String> keys = hostSecurityInterfaceRegexMap.get(d);
        if (keys == null) {
            return;
        }
        Long lDpid = null;
        if (!dpid.equals(DataNode.DEFAULT_STRING)) {
            lDpid = HexString.toLong(dpid);
        }
        String key = d.addressSpace + "|" + d.vlan + "|" + d.mac + "|"
                    + lDpid + "|" + ifaceName;

        keys.remove(key);
        if (keys.isEmpty())
            hostSecurityInterfaceRegexMap.remove(d);
        interfaceRegexMatcher.remove(key);

        /* CURRENTLY UNUSED:
         * use for exact matches
        SwitchInterface switchIface = new SwitchInterface(dpid, ifaceName);
        ConcurrentHashMap<Long, Collection<SwitchInterface>> mac2switchIface =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2switchIface == null) {
            return;
        }
        Collection<SwitchInterface> ifaces = mac2switchIface.get(mac);
        if (ifaces == null) {
            return;
        }
        ifaces.remove(switchIface);
        if (ifaces.isEmpty())
            mac2switchIface.remove(mac);
        if (mac2switchIface.isEmpty())
            hostSecurityInterfaceMap.remove(addrSpace);
        */
    }

    /**
     * Remove /all/ host to SwitchInterface anti-spoofing entries for the
     * given host. Need to hold config write-lock when calling this method.
     * @param addrSpace
     * @param vlan
     * @param mac
     */
    private void removeHostSecurityInterfaceEntry(String addrSpace, Short vlan, Long mac) {
        DeviceId host = new DeviceId(addrSpace, vlan, mac);
        if (logger.isDebugEnabled()) {
            logger.debug("Removing all security interfaces for Device=[{}]", host);
        }
        Collection<String> keys = hostSecurityInterfaceRegexMap.get(host);
        if (keys == null) {
            return;
        }
        for (String key: keys) {
            interfaceRegexMatcher.remove(key);
        }
        hostSecurityInterfaceRegexMap.remove(host);
    }

    /**
     * Clears in memory data structures that hold anti spoofing information.
     * ASSUMES caller holds the write lock.
     */
    protected void clearAntiSpoofingMemoryStructures() {
        this.hostSecurityIpMap.clear();
        this.hostSecurityInterfaceMap.clear();
        this.hostSecurityInterfaceRegexMap.clear();
        this.deviceIdToIpListMap.clear();
    }

    /**
     * Reads anti spoofing IP configuration from storage. Caller must hold
     * the lock.
     */
    private void readAntiSpoofingIPsFromStorage() throws BigDBException {
        Treespace t = bigDb.getControllerTreespace();
        String securityIpStr = "/core/device";
        Query securityIpQ = Query.builder()
                .setBasePath(securityIpStr)
                .setIncludedStateType(Query.StateType.CONFIG)
                .addSelectPath("security-ip-address")
                .getQuery();
        DataNodeSet deviceElements = t.queryData(securityIpQ, AuthContext.SYSTEM);
        for (DataNode deviceElement: deviceElements) {
            // For each device we add all the IPs
            String deviceId = deviceElement.getChild("id").getString();
            EntityWithClassName e = null;
            try {
                e = IndexedEntity.getNamedEntityFromKeyString(deviceId);
            } catch (Exception exc) {
                logger.error("Error decoding device for device ID=[{}]", deviceId);
                continue;
            }
            String addrSpace = e.getEntityClassName();
            Short vlan = null;
            Long mac = null;

            for (DeviceField df : e.getKeyFields()) {
                switch (df) {
                    case MAC:
                        mac = e.getEntity().getMacAddress();
                        break;
                    case VLAN:
                        vlan = e.getEntity().getVlan();
                        break;
                    case IPV4:
                        // ignore
                        break;
                    case PORT:
                        // ignore
                        break;
                    case SWITCH:
                        // ignore
                        break;
                    default:
                        logger.warn("Unknown device field {}", df);
                        break;
                }
            }

            DeviceId d = new DeviceId(addrSpace, vlan, mac);
            DataNode ipList = deviceElement.getChild("security-ip-address");
            for (DataNode ipaddr: ipList) {
                Integer ipInt = IPv4.toIPv4Address(ipaddr.getString());
                addAntiSpoofingIp2Mac(d, ipInt);
            }
        }
    }

    /**
     * Reads anti-spoofing attachment points from storage. Caller must
     * hold the lock.
     */
    private void readAntiSpoofingAPsFromStorage() throws BigDBException {
        Treespace t = bigDb.getControllerTreespace();

        String securityApStr = "/core/device";
        Query securityApQ = Query.builder()
                .setBasePath(securityApStr)
                .setIncludedStateType(Query.StateType.CONFIG)
                .addSelectPath("security-attachment-point")
                .getQuery();
        DataNodeSet deviceElements = t.queryData(securityApQ, AuthContext.SYSTEM);
        for (DataNode deviceElement: deviceElements) {
            // For each device we add all the IPs
            String deviceId = deviceElement.getChild("id").getString();
            EntityWithClassName e = null;
            try {
                e = IndexedEntity.getNamedEntityFromKeyString(deviceId);
            } catch (Exception exc) {
                logger.error("Error decoding device for device ID=[{}]", deviceId);
                continue;
            }
            String addrSpace = e.getEntityClassName();
            Short vlan = null;
            Long mac = null;

            for (DeviceField df : e.getKeyFields()) {
                switch (df) {
                    case MAC:
                        mac = e.getEntity().getMacAddress();
                        break;
                    case VLAN:
                        vlan = e.getEntity().getVlan();
                        break;
                    case IPV4:
                        // ignore
                        break;
                    case PORT:
                        // ignore
                        break;
                    case SWITCH:
                        // ignore
                        break;
                    default:
                        logger.warn("Unknown device field {}", df);
                        break;
                }
            }

            if ((vlan != null) && (!addrSpace.equals("default"))) {
                logger.error("Error, can not specify a VLAN [{}] if address space [{}] is not default",
                             vlan.toString(), addrSpace);
                continue;
            }

            DataNode securityAttachmentPoints =
                    deviceElement.getChild("security-attachment-point");
            DeviceId d = new DeviceId(addrSpace, vlan, mac);
            for (DataNode securityAttachmentPoint: securityAttachmentPoints) {
                String dpid = securityAttachmentPoint.getChild("dpid").getString();
                String intfRegex = securityAttachmentPoint.getChild("interface-regex").getString();
                addHostSecurityInterfaceEntry(d, dpid, intfRegex);

            }
        }
    }

    /**
     * Read anti-spoofing configuration from storage
     */
    protected void readAntiSpoofingConfigFromStorage() throws BigDBException {
        synchronized(antiSpoofingConfigWriteLock) {
            clearAntiSpoofingMemoryStructures();
            readAntiSpoofingIPsFromStorage();
            readAntiSpoofingAPsFromStorage();
        }
    }

    /*
     * allows for exact matching of switchInterfaces. CURRENTLY UNUSED SINCE
     * WE USE ONLY THE REGEX MATCHER FOR THE TIME BEING.
     */
    protected boolean checkHostSecurityInterfaceExact(String addrSpace,
                                                     Entity entity) {
        Long mac = entity.getMacAddress();
        Map<Long, Collection<SwitchInterface>> mac2SwitchPort =
                hostSecurityInterfaceMap.get(addrSpace);
        if (mac2SwitchPort == null) {
            // No config for this address space: allow
            return true;
        }
        Collection<SwitchInterface> switchInterfaces = mac2SwitchPort.get(mac);
        if (switchInterfaces == null || switchInterfaces.isEmpty()) {
            // no config for this Mac: allow
            return true;
        }
        for(SwitchInterface swi: switchInterfaces) {
            if (swi.getPortNumber(floodlightProvider) != null &&
                    topology.isConsistent(entity.getSwitchDPID(),
                                      (short)entity.getSwitchPort().intValue(),
                                      swi.getSwitchDPID(),
                                      swi.getPortNumber(floodlightProvider))) {
                // the port in the entity is consistent with one of the allowed
                // ports: allow the entity. We note that a port going into
                // a BD is consistent with every other port going to the same
                // BD.
                return true;
            }
        }
        return false;
    }

    @LogMessageDoc(level="WARN",
            message="Drop packet with srcMac equals to {virtualMac} " +
                    "for {services}",
            explanation="Dropped packet with Source MAC equal to virtual mac of"
                        + " service",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    @Override
    protected boolean isEntityAllowed(Entity entity, IEntityClass entityClass) {
        String addressSpaceName = entityClass.getName();
        Integer ip = entity.getIpv4Address();
        Long mac = entity.getMacAddress();
        Short vlan = null;
        if (entityClass.getKeyFields().contains(DeviceField.VLAN))
            vlan = entity.getVlan();
        DeviceId host = new DeviceId(addressSpaceName, vlan, mac);

        // First, check for IP spoofing
        if (ip != null) {
            ScopedIp scopedIp = new ScopedIp(addressSpaceName, ip);
            Collection<DeviceId> hosts = hostSecurityIpMap.get(scopedIp);
            if (hosts != null && (!hosts.contains(host))) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Device={} did not pass IP spoofing check", host);
                }
                return false;
            }
        }

        // check attachment point

        if (entity.getSwitchDPID() != null &&
            entity.getSwitchPort() != null &&
            !isValidAttachmentPoint(entity.getSwitchDPID(),
                                    entity.getSwitchPort())) {
            // not an AP port: allow
            return true;
        }

        Collection<String> keys = hostSecurityInterfaceRegexMap.get(host);
        if (keys == null) {
            // no config for this host: allow
            return true;
        }

        for (String key: keys) {
            Collection<SwitchPort> ifaces =
                    interfaceRegexMatcher.getInterfacesByKey(key);
            if (ifaces == null) {
                continue;
            }
            for (SwitchPort swp: ifaces) {
                if (topology.isAttachmentPointPort(swp.getSwitchDPID(),
                                                   (short)swp.getPort()) &&
                        topology.isConsistent(entity.getSwitchDPID(),
                                      (short)entity.getSwitchPort().intValue(),
                                      swp.getSwitchDPID(),
                                      (short)swp.getPort())) {
                    // the port in the entity is consistent with one of the allowed
                    // ports: allow the entity. We note that a port going into
                    // a BD is consistent with every other port going to the same
                    // BD.
                    return true;
                }
            }
        }
        return false;
    }

    //***************
    // IFloodlightModule
    //***************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDeviceTagService.class);
        l.addAll(super.getModuleServices());
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m =
                super.getServiceImpls();
        m.put(IDeviceTagService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        super.init(context);
        ecs = context.getServiceImpl(IEntityClassifierService.class);

        m_listeners = new CopyOnWriteArraySet<IDeviceTagListener>();
        m_lock = new ReentrantReadWriteLock();
        m_tags = new ConcurrentHashMap<String, Map<String, Set<DeviceTag>>>();

        tagToEntities = new ConcurrentHashMap<String, Set<EntityConfig>>();
        entityToTags = new ConcurrentHashMap<EntityConfig, Set<DeviceTag>>();

        antiSpoofingConfigWriteLock = new Object();
        interfaceRegexMatcher =
                new SwitchInterfaceRegexMatcher(floodlightProvider);
        hostSecurityIpMap =
                new ConcurrentHashMap<ScopedIp, Collection<DeviceId>>();
        hostSecurityInterfaceMap = new ConcurrentHashMap<String,
                ConcurrentHashMap<Long,Collection<SwitchInterface>>>();
        hostSecurityInterfaceRegexMap =
                new ConcurrentHashMap<DeviceId, Collection<String>>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) 
            throws FloodlightModuleException {
        // Our 'constructor'
        super.startUp(context);

        floodlightProvider.addOFSwitchListener(interfaceRegexMatcher);

        try {
            loadTagsFromStorage();
            readAntiSpoofingConfigFromStorage();
            Treespace t = bigDb.getControllerTreespace();
            logger.debug("Registering for mutation listener");
            Query tagMapping =
                    Query.parse(TAG_MANAGER_BIGDB_PATH);
            t.registerMutationListener(tagMapping, true, this);
            Query hostSecurityAP =
                    Query.parse(HOST_SECURITY_AP_BIGDB_PATH);
            t.registerMutationListener(hostSecurityAP, true, this);
            Query hostSecurityIP =
                    Query.parse(HOST_SECURITY_IP_BIGDB_PATH);
            t.registerMutationListener(hostSecurityIP, true, this);
        } catch (BigDBException e) {
            logger.error("Error registering BigDB mutation listener: {}", e);
        } catch (NullPointerException e) {
            logger.error("NPE when registering BigDB listener, {}", e);
        }

        // Attach our router
        try {
            ServerDataSource controllerDataSource =
                    bigDb.getControllerDataSource();
            controllerDataSource.registerDynamicDataHooksFromClass(
                    new Path("/core"), Path.EMPTY_PATH,
                    DeviceTagResource.class);
        } catch (BigDBException e) {
            logger.error("Error attaching BigDB resource: " + e);
        } catch (NullPointerException npe) {
            logger.error("BigDB is not initialized, couldn't attach router");
        }
    }

    /**
     * Handles the tag part of the BigDB backend.
     * @param query
     * @param operation
     * @throws BigDBException
     */
    private void handleTagManagerDataNodesMutated(Query query, Operation operation) throws BigDBException {
        try {
            acquireTagWriteLock();
            int numSteps = query.getSteps().size();
            if (numSteps < 2) {
                // needs to be tag-manager/tag-entity
                throw new UnsupportedOperationException();
            }
            switch (operation) {
                case MODIFY:
                    // these 3 fields are the primary key of a tag
                    String tagName = null;
                    String tagNameSpace = null;
                    String tagValue = null;
                    Step tagNameStep = query.getStep(1);
                    Query tagQuery = null;
                    // FIXME: RobV: I don't think tagNameStep can ever by null
                    // here so not sure why there's this check?
                    if (tagNameStep != null) {
                        // We are assuming all 3 predicates are passed in as get
                        // parameters.
                        tagName = tagNameStep.getExactMatchPredicateString(TAG_NAME_LEAF);
                        tagNameSpace = tagNameStep.getExactMatchPredicateString(TAG_NAMESPACE_LEAF);
                        if (tagNameSpace == null) {
                            // TODO - remove this once the default bug is fixed
                            tagNameSpace = "default";
                        }
                        tagValue = tagNameStep.getExactMatchPredicateString(TAG_VALUE_LEAF);
                        tagQuery = Query.builder()
                               .setBasePath(TAG_MANAGER_BIGDB_PATH +
                               '[' + TAG_NAME_LEAF + "=$tagname][" + TAG_NAMESPACE_LEAF + "=$tagnamespace][" +
                               TAG_VALUE_LEAF + "=$tagvalue]")
                               .setVariable("tagname", tagName)
                               .setVariable("tagnamespace", tagNameSpace)
                               .setVariable("tagvalue", tagValue)
                               .setIncludedStateType(Query.StateType.CONFIG)
                               .getQuery();
                    } else {
                        tagQuery = Query.builder()
                                .setBasePath(TAG_MANAGER_BIGDB_PATH)
                                .setIncludedStateType(Query.StateType.CONFIG)
                                .getQuery();
                    }
                    DataNodeSet result = bigDb.getControllerTreespace().queryData(tagQuery, AuthContext.SYSTEM);
                    if (tagNameStep != null) {
                        handleUpdateTagNode(result.getSingleDataNode());
                    } else {
                        // they have replaced the whole list
                        for (DataNode dataNode: result) {
                            handleUpdateTagNode(dataNode);
                        }
                    }
                    break;
                case DELETE:
                    Step tagStep = query.getStep(1);
                    String tName = tagStep.getExactMatchPredicateString(TAG_NAME_LEAF);
                    String tNameSpace = tagStep.getExactMatchPredicateString(TAG_NAMESPACE_LEAF);
                    String tValue = tagStep.getExactMatchPredicateString(TAG_VALUE_LEAF);
                    DeviceTag t = new DeviceTag(tNameSpace, tName, tValue);
                    // This checks wheter they specify /mapping or not
                    if (numSteps > 2)  {
                        Step mappingStep = query.getStep(2);
                        if (mappingStep.getName().equals("mapping")) {
                            // They are deleting a tag mapping
                            Step entityStep = query.getStep(2);
                            String eMac = entityStep.getExactMatchPredicateString("mac");
                            String eVlan = entityStep.getExactMatchPredicateString("vlan");
                            String eDpid = entityStep.getExactMatchPredicateString("dpid");
                            String eIntf = entityStep.getExactMatchPredicateString("interface-name");
                            EntityConfig e = new EntityConfig(eMac, eVlan, eDpid, eIntf);
                            removeEntityTagConfig(e, t);
                        }
                    } else {
                        // They are deleting a specific tag and it's associated mappings
                        deleteTagInternal(t);
                        TagManagerNotification notification =
                                new TagManagerNotification(t, null, TagManagerNotification.Action.DELETE_TAG);
                        notifyListeners(notification);
                    }
                    break;
            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    /**
     * Gets a device ID from
     * @param deviceStep The BigDB Step that contains the device part of the URL.
     * @return The deviceID as a string, null if not found.
     * @throws BigDBException if there was an error parsing the Step.
     */
    private String getDeviceId(Step deviceStep) throws BigDBException {
        String deviceId = deviceStep.getExactMatchPredicateString("id");
        if (deviceId == null) {
            logger.error("Got a request for a host security mapping with no device ID.");
            return deviceId;
        }
        return deviceId;
    }

    private void handleHostSecurityApDataNodesMutated(Query query, Operation operation) throws BigDBException {
        List<Step> steps = query.getSteps();
        Step deviceStep = steps.get(1);
        // We get the device ID then decode it into it's parts
        String deviceId = getDeviceId(deviceStep);
        if (deviceId == null) {
            throw new BigDBException("DeviceID not specified.");
        }
        EntityWithClassName entity;
        try {
            entity = IndexedEntity.getNamedEntityFromKeyString(deviceId);
        } catch (Exception e) {
            throw new BigDBException(e.getMessage());
        }

        String addrSpace = entity.getEntityClassName();
        Short vlan = null;
        Long mac = null;

        for (DeviceField df : entity.getKeyFields()) {
            switch (df) {
                case MAC:
                    mac = entity.getEntity().getMacAddress();
                    break;
                case VLAN:
                    vlan = entity.getEntity().getVlan();
                    break;
                case IPV4:
                    // ignore
                    break;
                case PORT:
                    // ignore
                    break;
                case SWITCH:
                    // ignore
                    break;
                default:
                    logger.warn("Unknown device field {}", df);
                    break;
            }
        }

        Query q = Query.builder()
                .setBasePath("/core/device[id=$deviceid]/security-attachment-point")
                .setVariable("deviceid", deviceId)
                .setIncludedStateType(Query.StateType.CONFIG)
                .getQuery();
        Treespace ts = bigDb.getControllerTreespace();
        DataNodeSet dnIntfList = ts.queryData(q, AuthContext.SYSTEM);
        DeviceId d = new DeviceId(addrSpace, vlan, mac);
        synchronized(antiSpoofingConfigWriteLock) {
            if (dnIntfList.isEmpty() && (operation.equals(Operation.DELETE))) {
                removeHostSecurityInterfaceEntry(addrSpace, vlan, mac);
            } else {
                for (DataNode le: dnIntfList) {
                    String ifaceNameRegex = le.getChild("interface-regex").getString();
                    String dpid = le.getChild("dpid").getString();
                    switch (operation) {
                        case MODIFY:
                            addHostSecurityInterfaceEntry(d, dpid, ifaceNameRegex);
                            break;
                        case DELETE:
                            removeHostSecurityInterfaceEntry(d, dpid, ifaceNameRegex);
                            break;
                    }
                }
            }
        }
    }

    private void handleHostSecurityIpDataNodesMutated(Query query, Operation operation) throws BigDBException {
        List<Step> steps = query.getSteps();
        Step deviceStep = steps.get(1);
        // We get the device ID then decode it into it's parts
        String deviceId = getDeviceId(deviceStep);
        if (deviceId == null) {
            throw new BigDBException("DeviceID not specified.");
        }
        EntityWithClassName entity;
        try {
            entity = IndexedEntity.getNamedEntityFromKeyString(deviceId);
        } catch (Exception e) {
            throw new BigDBException(e.getMessage());
        }

        String addrSpace = entity.getEntityClassName();
        Short vlan = null;
        Long mac = null;

        for (DeviceField df : entity.getKeyFields()) {
            switch (df) {
                case MAC:
                    mac = entity.getEntity().getMacAddress();
                    break;
                case VLAN:
                    vlan = entity.getEntity().getVlan();
                    break;
                case IPV4:
                    // ignore
                    break;
                case PORT:
                    // ignore
                    break;
                case SWITCH:
                    // ignore
                    break;
                default:
                    logger.warn("Unknown device field {}", df);
                    break;
            }
        }

        if ((vlan != null) && (!addrSpace.equals("default"))) {
            logger.error("Error, can not specify a VLAN [{}] if address space [{}] is not default",
                         vlan.toString(), addrSpace);
            return;
        }

        Query q = Query.builder()
                .setBasePath("/core/device[id=$deviceId]/security-ip-address")
                .setVariable("deviceId", deviceId)
                .setIncludedStateType(Query.StateType.CONFIG)
                .getQuery();
        Treespace ts = bigDb.getControllerTreespace();
        DataNodeSet dnIntfList = ts.queryData(q, AuthContext.SYSTEM);
        DeviceId adm = new DeviceId(addrSpace, vlan, mac);
        switch (operation) {
            case MODIFY:
                modifyAntiSpoofingIp2Mac(adm, dnIntfList);
                break;
            case DELETE:
                Collection<String> ipList = deviceIdToIpListMap.remove(adm);
                if (ipList == null || ipList.size() < 1) break;
                for (String ip : ipList) {
                    removeAntiSpoofingIp2Mac(adm, IPv4.toIPv4Address(ip));
                }
                break;
        }
    }

    private void modifyAntiSpoofingIp2Mac(DeviceId adm, DataNodeSet dnIntfList) throws BigDBException {
        synchronized(antiSpoofingConfigWriteLock) {
            Collection<String> ipList = deviceIdToIpListMap.get(adm);
            if (ipList == null) {
                // There are no existing IPs added, we can just add them all
                ipList = new ArrayList<String>();
                deviceIdToIpListMap.put(adm, ipList);
                for (DataNode le: dnIntfList) {
                    String ip = le.getString();
                    addAntiSpoofingIp2Mac(adm, IPv4.toIPv4Address(ip));
                    ipList.add(ip);
                }
            } else {
                // The list is not null, we have to reconcile and add/delete
                // the differences
                Collection<String> newIps = new ArrayList<String>();
                for (DataNode le: dnIntfList) {
                    newIps.add(le.getString());
                }
                deviceIdToIpListMap.put(adm, newIps);

                // take the new list and remove all the original ones
                // this leaves us with the items we have to add
                Collection<String> addIPs = new ArrayList<String>(newIps);
                addIPs.removeAll(ipList);
                for (String ip : addIPs) {
                    addAntiSpoofingIp2Mac(adm, IPv4.toIPv4Address(ip));
                }

                // take the original list and remove all the new ones
                // this leaves us with the ones we have to delete
                Collection<String> deleteList = new ArrayList<String>(ipList);
                deleteList.removeAll(newIps);
                for (String ip : deleteList) {
                    removeAntiSpoofingIp2Mac(adm, IPv4.toIPv4Address(ip));
                }
            }
        }
    }

    // TODO - this map and AddrVlanMac datastructure is a giant *#&$!@'ing hack because we don't have the data
    // that is being deleted. We need to find another way to keep track of this information.
    private final Map<DeviceId, Collection<String>> deviceIdToIpListMap = new ConcurrentHashMap<DeviceId, Collection<String>>();

    @Override
    public void dataNodesMutated(Set<Query> mutatedNodes, Operation operation,
            AuthContext authContext) throws BigDBException {
        for (Query query : mutatedNodes) {
            String basePath = query.getSimpleBasePath().toString();
            if (basePath.equals(TAG_MANAGER_BIGDB_PATH) || basePath.equals(TAG_MANGER_MAPPING_BIGDB_PATH)) {
                handleTagManagerDataNodesMutated(query, operation);
            } else if (basePath.equals(HOST_SECURITY_AP_BIGDB_PATH)) {
                handleHostSecurityApDataNodesMutated(query, operation);
            } else if (basePath.equals(HOST_SECURITY_IP_BIGDB_PATH)) {
                handleHostSecurityIpDataNodesMutated(query, operation);
            }
        }
    }

    /**
     * Handles adding a tag and all associated mappings.
     * @param tag The tag list element datanode.
     * @throws BigDBException If there was an error parsing the file.
     */
    private void handleUpdateTagNode(DataNode tag) throws BigDBException {
        String ns = tag.getChild(TAG_NAMESPACE_LEAF).getString();
        String name = tag.getChild(TAG_NAME_LEAF).getString();
        String tagVal = tag.getChild(TAG_VALUE_LEAF).getString();
        Boolean persist = tag.getChild(TAG_PERSIST_LEAF).getBoolean(true);

        DeviceTag t = createTag(ns, name, tagVal, persist);
        insertTag(t);

        Iterator<DataNode> mappingsIter = tag.getChild("mapping").iterator();
        while (mappingsIter.hasNext()) {
            handleUpdateTagMapping(t, mappingsIter.next());
        }
    }

    /**
     * Handles adding a mapping to a tag.
     * @param t The parent tag of the mapping.
     * @param m The DataNode that contains the tag mapping information.
     * @throws BigDBException
     */
    private void handleUpdateTagMapping(DeviceTag t, DataNode m) throws BigDBException{
        String mac = null;
        String dpid = null;
        String intf = null;
        Long vlan = null;

        for (DataNode.DictionaryEntry entry : m.getDictionaryEntries()) {
            if (entry.getName().equals(ENTITY_CONFIG_MAC_LEAF)) {
                mac = entry.getDataNode().getString();
            } else if (entry.getName().equals(ENTITY_CONFIG_DPID_LEAF)) {
                dpid = entry.getDataNode().getString();
            } else if (entry.getName().equals(ENTITY_CONFIG_INTERFACE_LEAF)) {
                intf = entry.getDataNode().getString();
            } else if (entry.getName().equals(ENTITY_CONFIG_VLAN_LEAF)) {
                vlan = entry.getDataNode().getLong();
            }
        }

        if ((dpid == null) && (intf != null)) {
            logger.error("Configuration error, entity spec has a " +
                    "interface specified but no dpid, rejecting " +
                    "this config - " + t.toString() + " " + mac + " " +
                    vlan.toString() + " " + intf);
            return;
            // TODO - handle deleting the data from BigDB so we are consistent?
        }
        EntityConfig entity = new EntityConfig(mac, vlan.toString(), dpid, intf);
        this.addEntityTagConfig(entity, t);
    }

    /**
     * Read the Tag information from storage
     */
    protected void loadTagsFromStorage() throws BigDBException {
        // Flush device mappings
        clearTagsFromMemory();

        // Gets all tags
        String tagQueryString = TAG_MANAGER_BIGDB_PATH;
        Query tagQuery = Query.builder()
                .setBasePath(tagQueryString)
                .setIncludedStateType(Query.StateType.CONFIG)
                .getQuery();
        DataNodeSet tags = bigDb.getControllerTreespace().queryData(tagQuery, AuthContext.SYSTEM);
        if (tags.isEmpty()) {
            // it's empty, we're done
            return;
        }
        try {
            acquireTagWriteLock();
            for (DataNode tag: tags) {
                handleUpdateTagNode(tag);
            }
        } finally {
            m_lock.writeLock().unlock();
        }
    }

    private Query getTagMappingQuery(DeviceTag t, EntityConfig e) throws BigDBException {
        Query query = Query.builder().setBasePath(TAG_MANAGER_BIGDB_PATH + '[' +
                TAG_NAME_LEAF + "=$tagname][" + TAG_NAMESPACE_LEAF + "=$tagnamespace][" +
                TAG_VALUE_LEAF + "=$tagvalue]" +
                "/mapping[mac=$tagmapmac][vlan=$tagmapvlan][dpid=$tagmapdpid][interface-name=$tagmapintfname]")
                .setVariable("tagname", t.getName())
                .setVariable("tagnamespace", t.getNamespace())
                .setVariable("tagvalue", t.getValue())
                .setVariable("tagmapmac", e.getMac())
                .setVariable("tagmapvlan", Short.parseShort(e.getVlan()))
                .setVariable("tagmapdpid", e.getDpid())
                .setVariable("tagmapintfname", e.getInterfaceName())
                .getQuery();
        return query;
    }
    /**
     * Adds a tag mapping to BigDB.
     * @param t The tag (all fields must be valid).
     * @param e The EntityConfig, i.e. the mapping.
     * @throws BigDBException If there was an error with the query.
     */
    private void addTagMappingToBigDB(DeviceTag t, EntityConfig e) throws BigDBException {
        InputStream is;
        try {
            ObjectMapper mapper = new ObjectMapper();
            String eJsonStr = mapper.writeValueAsString(e);
            is = new ByteArrayInputStream(eJsonStr.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException exc) {
            throw new BigDBException("Error mapping data to JSON for EntityConfig e = " + e);
        }
        Treespace ts = bigDb.getControllerTreespace();
        ts.replaceData(getTagMappingQuery(t, e), Treespace.DataFormat.JSON, is, AuthContext.SYSTEM);
    }

    /**
     * Deletes a tag mapping from BigDB.
     * @param t The tag (all fields must be valid).
     * @param e The EntityConfig, i.e. the mapping.
     * @throws BigDBException If there was an error with the query.
     */
    private void deleteTagMappingToBigDB(DeviceTag t, EntityConfig e) throws BigDBException {
        Treespace ts = bigDb.getControllerTreespace();
        ts.deleteData(getTagMappingQuery(t, e), AuthContext.SYSTEM);
    }

    /**
     * Acquires the tag write lock. This function checks to see
     * if it is currently being held by the current thread. Use this
     * to avoid deadlocks.
     */
    private void acquireTagWriteLock() {
        if (!m_lock.writeLock().isHeldByCurrentThread())
            m_lock.writeLock().lock();
    }
}
