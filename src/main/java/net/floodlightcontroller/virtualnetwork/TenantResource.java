package net.floodlightcontroller.virtualnetwork;

import java.io.IOException;

import net.floodlightcontroller.util.MACAddress;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.data.Status;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TenantResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(TenantResource.class);

    @Put
    public String updateTenant(String postData) {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        VirtualNetworkHost host = new VirtualNetworkHost();
        
        host.mac = MACAddress.valueOf((String) getRequestAttributes().get("mac"));
        String t = (String) getRequestAttributes().get("tenant");;
        if (!t.equals("0"))
            host.tenantId = t;
        
        host.ipAddresses = null;

        if(postData != null) {
            try {
                jsonToHost(postData, host);
            } catch (IOException e) {
                log.error("Could not parse JSON {}", e.getMessage());
            }
        }
        
        vns.updateHost(host);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
    
    protected void jsonToHost(String json, VirtualNetworkHost host) throws IOException {
        MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        
        try {
            jp = f.createJsonParser(json);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
        
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String n = jp.getCurrentName();
            jp.nextToken();
            if (jp.getText().equals("")) 
                continue;
            else if (n.equals("network")) {
                        host.guid = jp.getText();
            }
        }
        jp.close();
    }
}
