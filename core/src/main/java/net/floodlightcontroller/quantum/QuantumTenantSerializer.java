package net.floodlightcontroller.quantum;

import java.io.IOException;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Serializes a QuantumTenant for BigDB consumption.
 * @author alexreimers
 *
 */
public class QuantumTenantSerializer extends JsonSerializer<QuantumTenant> {

    @Override
    public void serialize(QuantumTenant qt, JsonGenerator jgen, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        jgen.writeObjectFieldStart(qt.getName());
        jgen.writeStartObject();
        jgen.writeStringField("name", qt.getName());
        Set<QuantumNetwork> nSet = qt.getNetworks();
        if ((nSet != null) && (nSet.size() > 0)) {
            jgen.writeArrayFieldStart("network");
            for (QuantumNetwork qn : nSet) {
                jgen.writeObject(qn);
            }
        }
        jgen.writeEndObject();
    }
}