package net.floodlightcontroller.quantum;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.query.Query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuantumHandlePort extends QuantumResource {
    protected static Logger log = LoggerFactory.getLogger(QuantumHandlePort.class);

    private QuantumPort jsonToQuantumDefinition(String json) throws IOException {
        QuantumPort q = mapper.readValue(json, QuantumPort.class);
        return q;
    }

    @Override
    protected InputStream getJsonInputData(String postData) throws Exception {
        QuantumPort qp = jsonToQuantumDefinition(postData);
        ObjectMapper mapper = new ObjectMapper();
        String qnJson = mapper.writeValueAsString(qp);
        return new ByteArrayInputStream(qnJson.getBytes("UTF-8"));
    }

    @Override
    protected Query getBigDBQuery() throws BigDBException {
        String tenant = (String) getRequestAttributes().get("tenant");
        String networkId = (String) getRequestAttributes().get("network");
        String port = (String) getRequestAttributes().get("port");
        Query q = null;
        if (port != null) {
            q = Query.builder().setBasePath("quantum/tenant[name=$tenant]/network[id=$network]/port[id=$port]")
            .setVariable("tenant", tenant)
            .setVariable("network", networkId)
            .setVariable("port", port)
            .getQuery();
        } else {
            q = Query.builder().setBasePath("quantum/tenant[name=$tenant]/network[id=$network]/port")
             .setVariable("tenant", tenant)
             .setVariable("network", networkId)
             .getQuery();
        }
        return q;
    }
}
