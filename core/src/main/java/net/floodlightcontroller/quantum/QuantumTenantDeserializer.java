package net.floodlightcontroller.quantum;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class QuantumTenantDeserializer extends JsonDeserializer<QuantumTenant> {

    @Override
    public QuantumTenant deserialize(JsonParser jp, DeserializationContext dc) throws IOException, JsonProcessingException {
        QuantumTenant qt = new QuantumTenant();
        ObjectCodec oc = jp.getCodec();
        JsonNode root = oc.readTree(jp);
        String name = root.get("name").asText();
        qt.setName(name);
        JsonNode networkList = root.get("network");
        if (networkList != null) {
            Iterator<JsonNode> networkIter = networkList.elements();
            while (networkIter.hasNext()) {
                JsonNode networkNode = networkIter.next();
                qt.addNetwork(oc.treeToValue(networkNode, QuantumNetwork.class));
            }
        }
        return qt;
    }
}
