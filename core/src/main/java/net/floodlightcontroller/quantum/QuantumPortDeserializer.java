package net.floodlightcontroller.quantum;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class QuantumPortDeserializer extends JsonDeserializer<QuantumPort> {

    @Override
    public QuantumPort deserialize(JsonParser jp, DeserializationContext dc)
            throws IOException, JsonProcessingException {
        QuantumPort qp = new QuantumPort();
        ObjectCodec oc = jp.getCodec();
        JsonNode root = oc.readTree(jp);
        String id = root.get("id").textValue();
        qp.setId(id);
        String state = root.get("state").textValue();
        qp.setState(state);

        JsonNode attachNode = root.get("attachment");
        if (attachNode != null) {
            qp.setAttachment(oc.treeToValue(attachNode, QuantumAttachment.class));
        }
        return qp;
    }
}
