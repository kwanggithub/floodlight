package net.floodlightcontroller.core.bigdb.serializers;

import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;
import org.openflow.util.U16;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;

public class OFMatchSerializer implements DataNodeSerializer<OFMatch> {
    public static final String IN_PORT_FIELD = "in-port";
    public static final String DL_DST_FIELD = "dl-dst";
    public static final String DL_SRC_FIELD = "dl-src";
    public static final String DL_VLAN_FIELD = "dl-vlan";
    public static final String DL_TYPE_FIELD = "dl-type";
    public static final String DL_VLAN_PCP_FIELD = "dl-vlan-pcp";
    public static final String NW_DST_FIELD = "nw-dst";
    public static final String NW_SRC_FIELD = "nw-src";
    public static final String NW_PROTO_FIELD = "nw-proto";
    public static final String NW_TOS_FIELD = "nw-tos";
    public static final String TP_DST_FIELD = "tp-dst";
    public static final String TP_SRC_FIELD = "tp-src";
    
    @Override
    public void serialize(OFMatch match, DataNodeGenerator gen)
            throws BigDBException {
        gen.writeMapStart();
        int wildcards = match.getWildcards();

        // Tweak the wildcards value to account for fields that aren't
        // applicable for the dl-type, so we don't include ignored fields in
        // the serialization of the match.
        int dlType = match.getDataLayerType();
        if (dlType == 0x0800) {
            // It's IP. The tp-src and tp-dst fields are only applicable if the
            // network protocol is ICMP (1), TCP (6) or UDP (17). So if it
            // isn't one of those fields wildcard tp-src and tp-dst.
            int nwProto = match.getNetworkProtocol();
            if ((nwProto != 1) && (nwProto != 6) && (nwProto != 17))
                wildcards |= (OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST);
        } else if (dlType == 0x0806) {
            // ARP: Also wildcard nw-tos
            wildcards |= (OFMatch.OFPFW_NW_TOS | OFMatch.OFPFW_TP_SRC |
                    OFMatch.OFPFW_TP_DST);
        } else {
            // Not IP or ARP: Wildcard all ip-related fields
            wildcards |= (OFMatch.OFPFW_NW_TOS | OFMatch.OFPFW_NW_PROTO |
                    OFMatch.OFPFW_NW_SRC_MASK | OFMatch.OFPFW_NW_DST_MASK |
                    OFMatch.OFPFW_TP_SRC | OFMatch.OFPFW_TP_DST);
        }

        if ((wildcards & OFMatch.OFPFW_IN_PORT) == 0) {
            gen.writeNumberField(IN_PORT_FIELD, match.getInputPort());
        }
        if ((wildcards & OFMatch.OFPFW_DL_DST) == 0) {
            gen.writeStringField(DL_DST_FIELD, 
                    HexString.toHexString(match.getDataLayerDestination()));
        }
        if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0) {
            gen.writeStringField(DL_SRC_FIELD, 
                    HexString.toHexString(match.getDataLayerSource()));
        }
        if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0) {
            gen.writeNumberField(DL_TYPE_FIELD, U16.f(match.getDataLayerType()));
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0) {
            gen.writeNumberField(DL_VLAN_FIELD, match.getDataLayerVirtualLan());
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0) {
            gen.writeNumberField(DL_VLAN_PCP_FIELD, 
                    match.getDataLayerVirtualLanPriorityCodePoint());
        }
        if (OFMatch.getNetworkDestinationMaskLen(wildcards) > 0) {
            gen.writeStringField(NW_DST_FIELD, 
                    OFMatch.cidrToString(match.getNetworkDestination(), 
                            match.getNetworkDestinationMaskLen()));
        }
        if (OFMatch.getNetworkSourceMaskLen(wildcards) > 0) {
            gen.writeStringField(NW_SRC_FIELD,
                    OFMatch.cidrToString(match.getNetworkSource(), 
                            match.getNetworkSourceMaskLen()));
        }
        if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0) {
            gen.writeNumberField(NW_PROTO_FIELD, match.getNetworkProtocol());
        }
        if ((wildcards & OFMatch.OFPFW_NW_TOS) == 0) {
            gen.writeNumberField(NW_TOS_FIELD, match.getNetworkTypeOfService());
        }
        if ((wildcards & OFMatch.OFPFW_TP_DST) == 0) {
            gen.writeNumberField(TP_DST_FIELD, U16.f(match.getTransportDestination()));
        }
        if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0) {
            gen.writeNumberField(TP_SRC_FIELD, U16.f(match.getTransportSource()));
        }
        gen.writeMapEnd();
    }
}
