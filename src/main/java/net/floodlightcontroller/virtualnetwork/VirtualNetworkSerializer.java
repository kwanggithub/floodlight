package net.floodlightcontroller.virtualnetwork;

import java.io.IOException;
import java.util.Iterator;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializerProvider;

/**
 * Serialize a VirtualNetwork object
 * @author KC Wang
 */
public class VirtualNetworkSerializer extends JsonSerializer<VirtualNetwork> {

    @Override
    public void serialize(VirtualNetwork vNet, JsonGenerator jGen,
            SerializerProvider serializer) throws IOException,
            JsonProcessingException {
        jGen.writeStartObject();
        
        jGen.writeStringField("name", vNet.name);
        jGen.writeStringField("guid", vNet.guid);
        jGen.writeStringField("gateway", vNet.gateway);
 
        jGen.writeArrayFieldStart("hosts");
        Iterator<VirtualNetworkHost> hit = vNet.hosts.values().iterator();
        while (hit.hasNext()) {
            VirtualNetworkHost host = hit.next();
            jGen.writeStartObject();
            jGen.writeStringField("id", host.hostId);
            jGen.writeStringField("port", host.port);
            jGen.writeStringField("mac", host.mac.toString());
            jGen.writeStringField("tenant", host.tenantId);
            jGen.writeStringField("network", host.guid);
            for (int i = 0; i < host.ipAddresses.size(); i++)
                jGen.writeStringField("ip", host.ipAddresses.get(i).toString());                
            jGen.writeEndObject();
        }
        jGen.writeEndArray();
        
        jGen.writeEndObject();
    }

}
