package org.projectfloodlight.device.tag;

import java.util.Iterator;
import java.util.Set;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.device.IDevice;
import org.projectfloodlight.device.internal.Device;
import org.projectfloodlight.device.internal.Entity;
import org.projectfloodlight.device.internal.IndexedEntity;

import com.google.common.collect.ImmutableSet;

public class DeviceTagResource extends FloodlightResource {

    @BigDBQuery
    @BigDBPath("device/tag")
    public static Iterable<DeviceTag> getDeviceTags(@BigDBParam("query") Query query) throws BigDBException {

        // FIXME: RobV: This code has not been tested!!!
        // I converted the code to work with the new way that operational state
        // is handled in BigDB, but I haven't verified that it works and I
        // don't think we have unit test coverage for this functionality.

        // Extract the device id from the query
        Step deviceStep = query.getStep(0);
        String id = deviceStep.getExactMatchPredicateString("id");
        if ((id == null) || id.isEmpty())
            throw new BigDBInternalError("Invalid null/empty host id");

        try {
            IndexedEntity ie = IndexedEntity.getIndexedEntityFromKeyString(id);
            // FIXME: Should check for conflicting values here, e.g.
            // multiple predicates with an exact match predicate on the id
            // and an exact match predicate on the mac where the mac values
            // don't match. Either treat as an error or an empty result.
            Entity entity = ie.getEntity();
            Long mac = entity.getMacAddress();
            Short vlan = entity.getVlan();
            Integer ip = entity.getIpv4Address();
            Long dpid = entity.getSwitchDPID();
            Integer port = entity.getSwitchPort();
            Iterator<? extends IDevice> diter =
                    getDeviceService().queryDevices(ie.getKeyFields(), mac,
                                                    vlan, ip, dpid, port);
            if (!diter.hasNext())
                return ImmutableSet.of();
            Device device = (Device) diter.next();
            if (diter.hasNext())
                throw new BigDBException("Multiple devices match device id");
            IDeviceTagService tagManager =
                    getModuleContext().getServiceImpl(IDeviceTagService.class);
            Set<DeviceTag> tags = ImmutableSet.copyOf(tagManager.getTagsByDevice(device));
            return tags;
        }
        catch (Exception e) {
            throw new BigDBException("Failed to parse device id: " + id, e);
        }
    }
}
