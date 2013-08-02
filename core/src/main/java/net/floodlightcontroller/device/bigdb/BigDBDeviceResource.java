package net.floodlightcontroller.device.bigdb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.bigdb.BigDBException;
import net.bigdb.BigDBInternalError;
import net.bigdb.data.annotation.BigDBPath;
import net.bigdb.data.annotation.BigDBParam;
import net.bigdb.data.annotation.BigDBQuery;
import net.bigdb.query.Query;
import net.bigdb.query.Step;
import net.floodlightcontroller.bigdb.FloodlightResource;
import net.floodlightcontroller.device.IDevice;
import net.floodlightcontroller.device.internal.AttachmentPoint;
import net.floodlightcontroller.device.internal.Device;
import net.floodlightcontroller.device.internal.Entity;
import net.floodlightcontroller.device.internal.IndexedEntity;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.FilterIterator;

import org.openflow.util.HexString;

public class BigDBDeviceResource extends FloodlightResource {

    protected static final String DEVICE_STEP_NAME = "device";
    protected static final String ID_FIELD_NAME = "id";
    protected static final String ATTACHMENT_POINT_LIST_NAME = "attachment-point";
    protected static final String MAC_FIELD_NAME = "mac";
    protected static final String VLAN_FIELD_NAME = "vlan";
    protected static final String IP_FIELD_NAME = "ip";
    protected static final String DPID_FIELD_NAME = ATTACHMENT_POINT_LIST_NAME + "/dpid";
    protected static final String PORT_FIELD_NAME = ATTACHMENT_POINT_LIST_NAME + "/port";

    public static class DeviceIterable implements Iterable<IDevice> {

        private Query query;

        DeviceIterable(Query query) {
            this.query = query;
        }

        @Override
        public Iterator<IDevice> iterator() {
            try {
                return getDeviceIterator(query);
            }
            catch (BigDBException e) {
                throw new BigDBInternalError(
                        "Unexpected BigDBException when getting device iterator", e);
            }
        }
    }

    @BigDBQuery
    public static Iterable<IDevice> getDevices(@BigDBParam("query") Query query) {
        return new DeviceIterable(query);
    }

    public static Iterator<IDevice> getDeviceIterator(Query query)
            throws BigDBException {

        assert query.getSteps() != null;
        assert query.getStep(0).getName().equals(DEVICE_STEP_NAME);
        Iterator<? extends IDevice> iter = queryDevices(query, 0);
        // FIXME: Skanky code to ensure that the device are returned in sorted
        // order. Should improve this to not require a TreeMap.
        Map<String, IDevice> sortMap = new TreeMap<String, IDevice>();
        while (iter.hasNext()) {
            Device device = (Device) iter.next();
            sortMap.put(device.getId(), device);
        }
        return sortMap.values().iterator();
    }

    @SuppressWarnings("unchecked")
    public static Iterator<? extends IDevice>
        queryDevices(Query query, int stepNumber)
            throws BigDBException {
        // FIXME: Why not just take a single Step argument instead of a query
        // and a step number.
        // FIXME: This implementation needs to be reworked in the context of
        // how the new dynamic state code works in the BigDB core code.
        Long mac = null;
        Short vlan = null;
        Integer ip = null;
        Long dpid = null;
        Integer port = null;

        Step deviceStep = query.getStep(stepNumber);

        final String id = deviceStep.getExactMatchPredicateString(ID_FIELD_NAME);
        String macStr = deviceStep.getExactMatchPredicateString(MAC_FIELD_NAME);
        Object vlanObj = deviceStep.getExactMatchPredicateValue(VLAN_FIELD_NAME);
        String ipStr = deviceStep.getExactMatchPredicateString(IP_FIELD_NAME);
        String dpidStr = deviceStep.getExactMatchPredicateString(DPID_FIELD_NAME);
        Object portObj = deviceStep.getExactMatchPredicateValue(PORT_FIELD_NAME);

        final String idStartsWith = deviceStep.getPrefixMatchPredicateString(ID_FIELD_NAME);
        final String macStartsWith = deviceStep.getPrefixMatchPredicateString(MAC_FIELD_NAME);
        final String vlanStartsWith = deviceStep.getPrefixMatchPredicateString(VLAN_FIELD_NAME);
        final String ipStartsWith = deviceStep.getPrefixMatchPredicateString(IP_FIELD_NAME);
        final String dpidStartsWith = deviceStep.getPrefixMatchPredicateString(DPID_FIELD_NAME);

        if (macStr != null) {
            try {
                mac = HexString.toLong(macStr);
            } catch (Exception e) {
                throw new BigDBException("Invalid mac address string: " + macStr);
            }
        }

        if (vlanObj != null) {
            try {
                if (vlanObj instanceof Long) {
                    vlan = ((Long)vlanObj).shortValue();
                } else {
                    vlan = Short.parseShort((String)vlanObj);
                }
                if (vlan > 4095 || vlan < 0) {
                    throw new BigDBException("Invalid vlan value: " +
                            vlanObj.toString());
                }
            } catch (RuntimeException e) {
                throw new BigDBException("Invalid vlan string: " +
                        vlanObj.toString());
            }
        }

        if (ipStr != null) {
            try {
                ip = IPv4.toIPv4Address(ipStr);
            } catch (Exception e) {
                throw new BigDBException("Invalid ipv4 string: " + ipStr);
            }
        }

        if (dpidStr != null) {
            try {
                dpid = HexString.toLong(dpidStr);
            } catch (Exception e) {
                throw new BigDBException("Invalid dpid string: " + dpid);
            }
        }

        if (portObj != null) {
            try {
                if (portObj instanceof Long) {
                    port = ((Long)portObj).intValue();
                } else {
                    port = Integer.parseInt((String)portObj);
                }
                if (port < 0) {
                    throw new BigDBException("Invalid port string: " + port);
                }
            } catch (RuntimeException e) {
                throw new BigDBException("Invalid port string: " + port);
            }
        }

        IndexedEntity ie = null;
        try {
            if (id != null && !id.isEmpty()) {
                ie = IndexedEntity.getIndexedEntityFromKeyString(id);
                // FIXME: Should check for conflicting values here, e.g.
                // multiple predicates with an exact match predicate on the id
                // and an exact match predicate on the mac where the mac values
                // don't match. Either treat as an error or an empty result.
                mac = ie.getEntity().getMacAddress();
                vlan = ie.getEntity().getVlan();
                ip = ie.getEntity().getIpv4Address();
                dpid = ie.getEntity().getSwitchDPID();
                port = ie.getEntity().getSwitchPort();
            }
        } catch (Exception e) {
            throw new BigDBException("Failed to parse device id: " + id, e);
        }

        // Do the query
        Iterator<Device> diter = null;
        if (ie != null) {
            diter = (Iterator<Device>) getDeviceService().queryDevices(
                    ie.getKeyFields(), mac, vlan, ip, dpid, port);
        } else {
            diter = (Iterator<Device>) getDeviceService().queryDevices(
                    mac, vlan,  ip, dpid, port);
        }

        // FIXME: It's not clear that there's much benefit to doing the
        // filtering manually here as opposed to just letting the core BigDB
        // predicate evaluation code do the work. Since the FilterIterator
        // is still going to iterate over all of the devices (or at least all
        // of the ones that are included in the queryDevices call above)
        // that's basically the same way logic that the core BigDB code uses.
        FilterIterator<Device> result = new FilterIterator<Device>(diter) {
            @Override
            protected boolean matches(Device value) {
                if (id != null ) {
                    if(!value.getId().equals(id)) {
                        return false;
                    }
                }
                if (idStartsWith != null) {
                    if (!value.getId().startsWith(idStartsWith))
                        return false;
                }
                if (macStartsWith != null) {
                    boolean match = false;
                    if (value.getMACAddressString() != null) {
                        match = value.getMACAddressString().startsWith(macStartsWith);
                    }
                    if (!match) {
                        return false;
                    }
                }
                if (vlanStartsWith != null) {
                    boolean match = false;
                    Short[] vlans = value.getVlanId();
                    if (vlans != null) {
                        for (Short vlan : vlans) {
                            if (vlan != null &&
                                Integer.toString(vlan).startsWith(vlanStartsWith)) {
                                match = true;
                                break;
                            }
                        }
                    }
                    if (!match) {
                        return false;
                    }
                }
                if (ipStartsWith != null) {
                    boolean match = false;
                    String[] ips = value.getIPAddresses();
                    if (ips != null) {
                        for (String ip : ips) {
                            if (ip != null && ip.startsWith(ipStartsWith)) {
                                match = true;
                                break;
                            }
                        }
                    }
                    if (!match) {
                        return false;
                    }
                }
                if (dpidStartsWith != null) {
                    boolean match = false;
                    AttachmentPoint[] aps = value.getBigDBAttachmentPoints();
                    for (AttachmentPoint ap : aps) {
                        String str = HexString.toHexString(ap.getSw(), 8);
                        if (str.startsWith(dpidStartsWith)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) return false;
                }
                /*
                if (portStartsWith != null) {
                    boolean match = false;
                    for (AttachmentPoint v : value.getBigDBAttachmentPoints()) {
                        if (v.getVlan() != null) {
                            String str = Integer.toString(v.getVlan());
                            if (v != null &&
                                    str.startsWith(dpidStartsWith)) {
                                match = true;
                                break;
                            }
                        }
                    }
                    if (!match) return false;
                }
                */
                return true;
            }
        };
        return result;
        /*
        if (query.getSteps().size() == 1) {
            List<Device> es = new LinkedList<Device>();
            while (result.hasNext()) {
                es.add(result.next());
            }
            return es;
        } else if (query.getSteps().get(1).getName().equals("entity")) {
            List<Entity> es = new LinkedList<Entity>();
            while (result.hasNext()) {
                Entity[] ee = result.next().getEntities();
                for (Entity e : ee) {
                    es.add(e);
                }
            }
            return es;
        } else if (query.getSteps().get(1).getName().equals("attachment-point")) {
            List<AttachmentPoint> es = new LinkedList<AttachmentPoint>();
            while (result.hasNext()) {
                AttachmentPoint[] ee = result.next().getBigDBAttachmentPoints();
                for (AttachmentPoint e : ee) {
                    es.add(e);
                }
            }
            return es;
        } else {
            throw new BigDBException("Unsupported query node name: " +
                                      query.getSteps().get(1).getName());
        }
        */
    }

    @BigDBQuery
    @BigDBPath("entity")
    public static List<Entity> getDeviceEntities(@BigDBParam("query") Query query)
            throws BigDBException {
        // FIXME: This should really return a List<Entity[]>
        // Core code needs some improvements to support that though.
        Query deviceQuery = query.subQuery(0, query.getSteps().size() - 1);
        Iterator<IDevice> deviceIter = getDeviceIterator(deviceQuery);
        List<Entity> result = new ArrayList<Entity>();
        while (deviceIter.hasNext()) {
            Device device = (Device) deviceIter.next();
            Entity[] entities = device.getEntities();
            for (Entity e: entities)
                result.add(e);
        }
        return result;
    }
}
