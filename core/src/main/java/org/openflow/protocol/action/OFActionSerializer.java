package org.openflow.protocol.action;

import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U32;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.packet.IPv4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OFAction serializer for BigDB.
 * @author alexreimers
 *
 */
public class OFActionSerializer implements DataNodeSerializer<OFAction> {
    protected static final Logger log = 
            LoggerFactory.getLogger(OFActionSerializer.class);
    
    @Override
    public void serialize(OFAction action, DataNodeGenerator gen) throws BigDBException {
        gen.writeMapStart();
        gen.writeStringField("action-type", 
                action.getType().toString().toLowerCase().replace('_', '-'));
        
        switch (action.getType()) {
            case OUTPUT:
                OFActionOutput ofout = (OFActionOutput) action;
                gen.writeMapFieldStart("output-data");
                gen.writeNumberField("port", U16.f(ofout.getPort()));
                gen.writeNumberField("max-length", U16.f(ofout.getMaxLength()));
                gen.writeMapEnd();
                break;
            case OPAQUE_ENQUEUE:
                OFActionEnqueue ofenq = (OFActionEnqueue) action;
                gen.writeMapFieldStart("opaque-enqueue-data");
                gen.writeNumberField("port", U16.f(ofenq.getPort()));
                gen.writeNumberField("queue-id", U32.f(ofenq.getQueueId()));
                gen.writeMapEnd();
                break;
            case SET_DL_DST:
                OFActionDataLayerDestination ofdldst = 
                    (OFActionDataLayerDestination) action;
                gen.writeMapFieldStart("set-dl-dst-data");
                gen.writeStringField("dst-dl-addr", HexString.toHexString(ofdldst.getDataLayerAddress()));
                gen.writeMapEnd();
                break;
            case SET_DL_SRC:
                OFActionDataLayerSource ofdlsrc =
                    (OFActionDataLayerSource) action;
                gen.writeMapFieldStart("set-dl-src-data");
                gen.writeStringField("src-dl-addr", HexString.toHexString(ofdlsrc.getDataLayerAddress()));
                gen.writeMapEnd();
                break;
            case SET_NW_DST:
                OFActionNetworkLayerDestination ofnwdst = (OFActionNetworkLayerDestination) action;
                gen.writeMapFieldStart("set-nw-dst-data");
                gen.writeStringField("dst-nw-addr", IPv4.fromIPv4Address(ofnwdst.getNetworkAddress()));
                gen.writeMapEnd();
                break;
            case SET_NW_SRC:
                OFActionNetworkLayerSource ofnwsrc = (OFActionNetworkLayerSource) action;
                gen.writeMapFieldStart("set-nw-src-data");
                gen.writeStringField("src-nw-addr", IPv4.fromIPv4Address(ofnwsrc.getNetworkAddress()));
                gen.writeMapEnd();
                break;
            case SET_NW_TOS:
                OFActionNetworkTypeOfService ofnwtos = (OFActionNetworkTypeOfService) action;
                gen.writeMapFieldStart("set-nw-tos-data");
                gen.writeNumberField("tos", ofnwtos.getNetworkTypeOfService());
                gen.writeMapEnd();
                break;
            case SET_TP_DST:
                OFActionTransportLayerDestination oftpdst = (OFActionTransportLayerDestination) action;
                gen.writeMapFieldStart("set-tp-dst-data");
                gen.writeNumberField("dst-port", oftpdst.getTransportPort());
                gen.writeMapEnd();
                break;
            case SET_TP_SRC:
                OFActionTransportLayerSource oftpsrc = (OFActionTransportLayerSource) action;
                gen.writeMapFieldStart("set-tp-src-data");
                gen.writeNumberField("src-port", oftpsrc.getTransportPort());
                gen.writeMapEnd();
                break;
            case SET_VLAN_ID:
                OFActionVirtualLanIdentifier ofvlan = (OFActionVirtualLanIdentifier) action;
                gen.writeMapFieldStart("set-vlan-id-data");
                gen.writeNumberField("vlan-id", ofvlan.getVirtualLanIdentifier());
                gen.writeMapEnd();
                break;
            case SET_VLAN_PCP:
                OFActionVirtualLanPriorityCodePoint ofpcp = 
                    (OFActionVirtualLanPriorityCodePoint) action;
                gen.writeMapFieldStart("set-vlan-pcp-data");
                gen.writeNumberField("vlan-pcp", ofpcp.getVirtualLanPriorityCodePoint());
                gen.writeMapEnd();
                break;
            case STRIP_VLAN:
                // NO-OP as there is no data
                break;
            case VENDOR:
                log.warn("Serializer for OFType VENDOR not implemented");
                break;
            default:
                log.warn("No serializer for OFType " + action.getType().toString());
                break;
        }
        gen.writeMapEnd();
    }
}
