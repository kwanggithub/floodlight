package org.projectfloodlight.core.bigdb.serializers;

import org.openflow.protocol.OFMatch;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;

/**
 * Serializes an OpenFlow 1.0 table match wildcards into the string fields.
 * @author alexreimers
 *
 */
public class OFTableWildcardSerializer implements DataNodeSerializer<Integer> {

    @Override
    public void serialize(Integer wc, DataNodeGenerator gen) throws BigDBException {
        gen.writeListStart();
        int wildcards = wc.intValue();
        if ((wildcards & OFMatch.OFPFW_IN_PORT) == 0) {
            gen.writeString(OFMatchSerializer.IN_PORT_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_DL_DST) == 0) {
            gen.writeString(OFMatchSerializer.DL_DST_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0) {
            gen.writeString(OFMatchSerializer.DL_SRC_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0) {
            gen.writeString(OFMatchSerializer.DL_TYPE_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0) {
            gen.writeString(OFMatchSerializer.DL_VLAN_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0) {
            gen.writeString(OFMatchSerializer.DL_VLAN_PCP_FIELD);
        }
        if (OFMatch.getNetworkDestinationMaskLen(wildcards) > 0) {
            gen.writeString(OFMatchSerializer.NW_DST_FIELD);
        }
        if (OFMatch.getNetworkSourceMaskLen(wildcards) > 0) {
            gen.writeString(OFMatchSerializer.NW_SRC_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0) {
            gen.writeString(OFMatchSerializer.NW_PROTO_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_NW_TOS) == 0) {
            gen.writeString(OFMatchSerializer.NW_TOS_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_TP_DST) == 0) {
            gen.writeString(OFMatchSerializer.TP_DST_FIELD);
        }
        if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0) {
            gen.writeString(OFMatchSerializer.TP_SRC_FIELD);
        }
        gen.writeListEnd();
    }

}
