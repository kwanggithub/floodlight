package net.floodlightcontroller.quantum;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;

public class QuantumNetworkDeserializer extends JsonDeserializer<QuantumNetwork> {

    @Override
    public QuantumNetwork deserialize(JsonParser jp, DeserializationContext dc)
            throws IOException, JsonProcessingException {
        QuantumNetwork qn = new QuantumNetwork();
        ObjectCodec oc = jp.getCodec();
        JsonNode root = oc.readTree(jp);
        root = root.get("network");
        String id = root.get("id").textValue();
        qn.setId(id);
        JsonNode nameNode = root.get("name");
        if (nameNode != null) {
            qn.setName(nameNode.textValue());
        }
        JsonNode gwNode = root.get("gateway");
        if (gwNode != null) {
            qn.setGateway(gwNode.textValue());
        }
        JsonNode stateNode = root.get("state");
        if (stateNode != null) {
            qn.setState(stateNode.textValue());
        }

        JsonNode portList = root.get("port");
        if (portList != null) {
            Iterator<JsonNode> portIter = portList.elements();
            while (portIter.hasNext()) {
                JsonNode portNode = portIter.next();
                qn.addPort(oc.treeToValue(portNode, QuantumPort.class));
            }
        }
        return qn;
    }

}
