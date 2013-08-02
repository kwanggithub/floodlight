package net.floodlightcontroller.quantum;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class QuantumAttachmentSerializer extends
        JsonSerializer<QuantumAttachment> {

    @Override
    public void serialize(QuantumAttachment qa, JsonGenerator jgen, SerializerProvider sp)
            throws IOException, JsonProcessingException {
        /*
        "attachment": {
              "id": "158233b0-ca9a-40b4-8614-54a4a99d47e1",
              "mac": "01:02:03:04:05:06",
              "state": "UP"
        }
        */
        jgen.writeObjectFieldStart("attachment");
        jgen.writeStringField("id", qa.getId());
        jgen.writeStringField("mac", qa.getMac().toString());
        // We default to sending up state
        jgen.writeStringField("state", qa.getState() == null ? "UP" : qa.getState());
        jgen.writeEndObject();
    }

}
