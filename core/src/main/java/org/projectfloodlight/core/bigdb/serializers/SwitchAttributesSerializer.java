package org.projectfloodlight.core.bigdb.serializers;

import java.util.Map;

import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

/**
 * Serializes the IOFswitch.getAttributes() call.
 * @author alexreimers
 *
 */
public class SwitchAttributesSerializer implements DataNodeSerializer<Map<String, Object>> {

    @Override
    public void serialize(Map<String, Object> map, DataNodeGenerator gen) throws BigDBException {
        gen.writeMapStart();
        Object descripO = map.get(IOFSwitch.SWITCH_DESCRIPTION_DATA);
        if ((descripO != null) && (descripO instanceof OFDescriptionStatistics)) {
            OFDescriptionStatistics descStats = (OFDescriptionStatistics) descripO;
            gen.writeMapFieldStart("description-data");
            gen.writeStringField("manufacturer-description", descStats.getManufacturerDescription());
            gen.writeStringField("hardware-description", descStats.getHardwareDescription());
            gen.writeStringField("software-description", descStats.getSoftwareDescription());
            gen.writeStringField("serial-number", descStats.getSerialNumber());
            gen.writeStringField("datapath-description", descStats.getDatapathDescription());
            gen.writeMapEnd();
        }
        Object fwO = map.get(IOFSwitch.PROP_FASTWILDCARDS);
        if (fwO instanceof Integer) {
            gen.writeNumberField(IOFSwitch.PROP_FASTWILDCARDS, (Integer)fwO);
        }
        Object nxO = map.get(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE);
        if (nxO instanceof Boolean) {
            gen.writeBooleanField(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE, (Boolean)nxO);
        }
        Object floodO = map.get(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD);
        if (floodO instanceof Boolean) {
            gen.writeBooleanField(IOFSwitch.PROP_SUPPORTS_OFPP_FLOOD, (Boolean)floodO);
        }
        Object tableO = map.get(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE);
        if (tableO instanceof Boolean) {
            gen.writeBooleanField(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE, (Boolean)tableO);
        }
        gen.writeMapEnd();
    }

}
