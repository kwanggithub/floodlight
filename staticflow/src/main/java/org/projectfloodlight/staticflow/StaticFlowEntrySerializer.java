package org.projectfloodlight.staticflow;

import java.io.IOException;
import java.util.List;

import net.floodlightcontroller.core.bigdb.serializers.OFMatchSerializer;
import net.floodlightcontroller.packet.IPv4;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDataLayerDestination;
import org.openflow.protocol.action.OFActionDataLayerSource;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionNetworkLayerDestination;
import org.openflow.protocol.action.OFActionNetworkLayerSource;
import org.openflow.protocol.action.OFActionNetworkTypeOfService;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionTransportLayerDestination;
import org.openflow.protocol.action.OFActionTransportLayerSource;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.protocol.action.OFActionVirtualLanPriorityCodePoint;
import org.openflow.util.HexString;
import org.openflow.util.U16;
import org.openflow.util.U32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO - Change this to a BigDB serializer when we have support
public class StaticFlowEntrySerializer extends JsonSerializer<StaticFlowEntry> {
    private static Logger log = LoggerFactory.getLogger(StaticFlowEntrySerializer.class);
    @Override
    public void serialize(StaticFlowEntry sfe, JsonGenerator gen, SerializerProvider sp) throws IOException,
            JsonProcessingException {
        gen.writeStartObject();
        gen.writeBooleanField("active", sfe.isActive());
        gen.writeStringField("name", sfe.getName());
        gen.writeObjectFieldStart("flow-mod");
        gen.writeNumberField("priority", sfe.getFlowMod().getPriority());
        gen.writeNumberField("cookie", sfe.getFlowMod().getCookie());
        gen.writeObjectFieldStart("match");
        serializeOFMatch(sfe.getFlowMod().getMatch(), gen);
        gen.writeEndObject();
        gen.writeArrayFieldStart("action");
        List<OFAction> actionL = sfe.getFlowMod().getActions();
        if ((actionL != null) && (!actionL.isEmpty())) {
            int seq = 0;
            for (OFAction a : actionL) {
                seq++;
                gen.writeStartObject();
                gen.writeNumberField("sequence", seq);
                switch (a.getType()) {
                case DROP:
                    log.warn("DROP action specified with other actions");
                    break;
                case OPAQUE_ENQUEUE:
                    OFActionEnqueue en = (OFActionEnqueue) a;
                    gen.writeStringField("action-type", "opaque-enqueue");
                    gen.writeObjectFieldStart("opaque-enqueue-data");
                    gen.writeNumberField("port", en.getPort());
                    gen.writeNumberField("queue-id", en.getQueueId());
                    gen.writeEndObject();
                    break;
                case OUTPUT:
                    OFActionOutput o = (OFActionOutput) a;
                    gen.writeStringField("action-type", "output");
                    gen.writeObjectFieldStart("output-data");
                    gen.writeNumberField("port", U16.f(o.getPort()));
                    gen.writeNumberField("max-length", U32.f(o.getMaxLength()));
                    gen.writeEndObject();
                    break;
                case SET_DL_DST:
                    OFActionDataLayerDestination dl = (OFActionDataLayerDestination) a;
                    gen.writeStringField("action-type", "set-dl-dst");
                    gen.writeObjectFieldStart("set-dl-dst-data");
                    gen.writeStringField("dst-dl-addr", HexString.toHexString(dl.getDataLayerAddress()));
                    gen.writeEndObject();
                    break;
                case SET_DL_SRC:
                    OFActionDataLayerSource dlsrc = (OFActionDataLayerSource) a;
                    gen.writeStringField("action-type", "set-dl-src");
                    gen.writeObjectFieldStart("set-dl-src-data");
                    gen.writeStringField("src-dl-addr", HexString.toHexString(dlsrc.getDataLayerAddress()));
                    gen.writeEndObject();
                    break;
                case SET_NW_DST:
                    OFActionNetworkLayerDestination nwDst = (OFActionNetworkLayerDestination) a;
                    gen.writeStringField("action-type", "set-nw-dst");
                    gen.writeObjectFieldStart("set-nw-dst-data");
                    gen.writeStringField("dst-nw-addr", IPv4.fromIPv4Address(nwDst.getNetworkAddress()));
                    gen.writeEndObject();
                    break;
                case SET_NW_SRC:
                    OFActionNetworkLayerSource nwSrc = (OFActionNetworkLayerSource) a;
                    gen.writeStringField("action-type", "set-nw-src");
                    gen.writeObjectFieldStart("set-nw-src-data");
                    gen.writeStringField("src-nw-addr", IPv4.fromIPv4Address(nwSrc.getNetworkAddress()));
                    gen.writeEndObject();
                    break;
                case SET_NW_TOS:
                    OFActionNetworkTypeOfService nwTos = (OFActionNetworkTypeOfService) a;
                    gen.writeStringField("action-type", "set-nw-tos");
                    gen.writeObjectFieldStart("set-nw-tos-data");
                    gen.writeNumberField("tos", nwTos.getNetworkTypeOfService());
                    gen.writeEndObject();
                    break;
                case SET_TP_DST:
                    OFActionTransportLayerDestination tpDst = (OFActionTransportLayerDestination) a;
                    gen.writeStringField("action-type", "set-tp-dst");
                    gen.writeObjectFieldStart("set-tp-dst-data");
                    gen.writeNumberField("dst-port", tpDst.getTransportPort());
                    gen.writeEndObject();
                    break;
                case SET_TP_SRC:
                    OFActionTransportLayerSource tpSrc = (OFActionTransportLayerSource) a;
                    gen.writeStringField("action-type", "set-tp-src");
                    gen.writeObjectFieldStart("set-tp-src-data");
                    gen.writeNumberField("src-port", tpSrc.getTransportPort());
                    gen.writeEndObject();
                    break;
                case SET_VLAN_ID:
                    OFActionVirtualLanIdentifier setV = (OFActionVirtualLanIdentifier) a;
                    gen.writeStringField("action-type", "set-vlan-id");
                    gen.writeString("set-vlan-id-data");
                    gen.writeStartObject();
                    gen.writeNumberField("vlan-id", setV.getVirtualLanIdentifier());
                    gen.writeEndObject();
                    break;
                case SET_VLAN_PCP:
                    OFActionVirtualLanPriorityCodePoint pcp = (OFActionVirtualLanPriorityCodePoint) a;
                    gen.writeStringField("action-type", "set-nw-tos");
                    gen.writeObjectFieldStart("set-vlan-pcp-data");
                    gen.writeNumberField("vlan-pcp", pcp.getVirtualLanPriorityCodePoint());
                    gen.writeEndObject();
                    break;
                case STRIP_VLAN:
                    // no data for strip vlan
                    gen.writeStringField("action-type", "strip-vlan");
                    break;
                case VENDOR:
                    log.warn("Vendor specific action given, nothing being done");
                    break;
                default:
                    log.warn("Action {} unrecognized.", a.getType());
                    break;
                }
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
        gen.writeEndObject();
        gen.writeEndObject();
    }

    private void serializeOFMatch(OFMatch match, JsonGenerator gen) throws JsonGenerationException, IOException {
        int wildcards = match.getWildcards();
        if ((wildcards & OFMatch.OFPFW_IN_PORT) == 0) {
            gen.writeNumberField(OFMatchSerializer.IN_PORT_FIELD, match.getInputPort());
        }
        if ((wildcards & OFMatch.OFPFW_DL_DST) == 0) {
            gen.writeStringField(OFMatchSerializer.DL_DST_FIELD,
                    HexString.toHexString(match.getDataLayerDestination()));
        }
        if ((wildcards & OFMatch.OFPFW_DL_SRC) == 0) {
            gen.writeStringField(OFMatchSerializer.DL_SRC_FIELD,
                    HexString.toHexString(match.getDataLayerSource()));
        }
        if ((wildcards & OFMatch.OFPFW_DL_TYPE) == 0) {
            gen.writeNumberField(OFMatchSerializer.DL_TYPE_FIELD, match.getDataLayerType());
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN) == 0) {
            gen.writeNumberField(OFMatchSerializer.DL_VLAN_FIELD, match.getDataLayerVirtualLan());
        }
        if ((wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == 0) {
            gen.writeNumberField(OFMatchSerializer.DL_VLAN_PCP_FIELD,
                    match.getDataLayerVirtualLanPriorityCodePoint());
        }
        if (match.getNetworkDestinationMaskLen() > 0) {
            gen.writeStringField(OFMatchSerializer.NW_DST_FIELD,
                    OFMatch.cidrToString(match.getNetworkDestination(),
                            match.getNetworkDestinationMaskLen()));
        }
        if (match.getNetworkSourceMaskLen() > 0) {
            gen.writeStringField(OFMatchSerializer.NW_SRC_FIELD,
                    OFMatch.cidrToString(match.getNetworkSource(),
                            match.getNetworkSourceMaskLen()));
        }
        if ((wildcards & OFMatch.OFPFW_NW_PROTO) == 0) {
            gen.writeNumberField(OFMatchSerializer.NW_PROTO_FIELD, match.getNetworkProtocol());
        }
        if ((wildcards & OFMatch.OFPFW_NW_TOS) == 0) {
            gen.writeNumberField(OFMatchSerializer.NW_TOS_FIELD, match.getNetworkTypeOfService());
        }
        if ((wildcards & OFMatch.OFPFW_TP_DST) == 0) {
            gen.writeNumberField(OFMatchSerializer.TP_DST_FIELD, match.getTransportDestination());
        }
        if ((wildcards & OFMatch.OFPFW_TP_SRC) == 0) {
            gen.writeNumberField(OFMatchSerializer.TP_SRC_FIELD, match.getTransportSource());
        }
    }
}
