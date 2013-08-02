package net.floodlightcontroller.quantum;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.query.Query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuantumHandleNetwork extends QuantumResource {
    protected static Logger log = LoggerFactory.getLogger(QuantumHandleNetwork.class);

    private QuantumNetwork jsonToNetworkDefinition(String json) throws IOException {
        QuantumNetwork network = mapper.readValue(json, QuantumNetwork.class);
        return network;
    }

    @Override
    protected InputStream getJsonInputData(String postData) throws Exception {
        QuantumNetwork qn = jsonToNetworkDefinition(postData);
        ObjectMapper mapper = new ObjectMapper();
        String qnJson = mapper.writeValueAsString(qn);
        return new ByteArrayInputStream(qnJson.getBytes("UTF-8"));
    }

    @Override
    protected Query getBigDBQuery() throws BigDBException {
        String tenant = (String) getRequestAttributes().get("tenant");
        String networkId = (String) getRequestAttributes().get("network");
        Query q = null;
        if (tenant != null && networkId != null) {
            q = Query.builder().setBasePath("quantum/tenant[name=$tenant]/network[id=$network]")
            .setVariable("tenant", tenant)
            .setVariable("network", networkId)
            .getQuery();
        } else {
            q = Query.parse("quantum/tenant[name=$tenant]/network", "tenant", tenant);
        }
        return q;
    }
}
