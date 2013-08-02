package net.floodlightcontroller.quantum;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bigdb.BigDBException;
import net.bigdb.query.Query;

public class QuantumHandleAttachment extends QuantumResource {

    @Override
    protected Query getBigDBQuery() throws BigDBException {
        String tenant = (String) getRequestAttributes().get("tenant");
        String networkId = (String) getRequestAttributes().get("network");
        String port = (String) getRequestAttributes().get("port");
        Query q = Query.builder().setBasePath("quantum/tenant[name=$tenant]/network[id=$network]/port[id=$port]")
            .setVariable("tenant", tenant)
            .setVariable("network", networkId)
            .setVariable("port", port)
            .getQuery();
        return q;
    }

    @Override
    protected InputStream getJsonInputData(String postData) throws Exception {
        QuantumAttachment qp =  mapper.readValue(postData, QuantumAttachment.class);
        ObjectMapper mapper = new ObjectMapper();
        String qnJson = mapper.writeValueAsString(qp);
        return new ByteArrayInputStream(qnJson.getBytes("UTF-8"));
    }

}
