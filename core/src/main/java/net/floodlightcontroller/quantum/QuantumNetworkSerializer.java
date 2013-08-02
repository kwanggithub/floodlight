package net.floodlightcontroller.quantum;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializes a QuantumNetwork for BigDB consumption.
 * @author alexreimers
 */
public class QuantumNetworkSerializer extends JsonSerializer<QuantumNetwork> {
    @Override
    public void serialize(QuantumNetwork qn, JsonGenerator jgen, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        jgen.writeStartObject();
        jgen.writeStringField("name", qn.getName());
        jgen.writeStringField("id", qn.getId());
        String gateway = qn.getGateway();
        if (gateway != null) {
            jgen.writeStringField("gateway", gateway);
        }
        String state = qn.getState();
        if (state != null) {
            jgen.writeStringField("state", state);
        }
        Set<QuantumPort> ports = qn.getPorts();
        if ((ports != null) && (ports.size() > 0)) {
            jgen.writeArrayFieldStart("port");
            for (QuantumPort p : ports) {
                jgen.writeObject(p);
            }
            jgen.writeEndArray();
        }
        jgen.writeEndObject();
    }
}
