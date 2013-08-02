package net.floodlightcontroller.quantum;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Handles serialization of a QuantumPort for BigDB consumption.
 * @author alexreimers
 *
 */
public class QuantumPortSerializer extends JsonSerializer<QuantumPort> {

    @Override
    public void serialize(QuantumPort qp, JsonGenerator jgen, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        jgen.writeObjectFieldStart(qp.getId());
        jgen.writeStringField("id", qp.getId());
        jgen.writeStringField("state", qp.getState());
        QuantumAttachment qa = qp.getAttachment();
        if (qa != null) {
            jgen.writeObject(qa);
        }
        jgen.writeEndObject();
    }
}
