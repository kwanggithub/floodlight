package net.floodlightcontroller.quantum;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.query.Query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuantumHandleTenant extends QuantumResource {
    protected static Logger log = LoggerFactory.getLogger(QuantumHandleTenant.class);

    private QuantumTenant jsonToQuantumDefinition(String json) throws IOException {
        QuantumTenant q = mapper.readValue(json, QuantumTenant.class);
        return q;
    }

    @Override
    protected InputStream getJsonInputData(String postData) throws Exception {
        QuantumTenant qn = jsonToQuantumDefinition(postData);
        ObjectMapper mapper = new ObjectMapper();
        String qnJson = mapper.writeValueAsString(qn);
        return new ByteArrayInputStream(qnJson.getBytes("UTF-8"));
    }

    @Override
    protected Query getBigDBQuery() throws BigDBException {
        String tenant = (String) getRequestAttributes().get("tenant");
        Query q = null;
        if (tenant != null) {
            q = Query.parse("quantum/tenant[name=$tenant]", "tenant", tenant);
        } else {
            q = Query.parse("quantum/tenant");
        }
        return q;
    }
}
