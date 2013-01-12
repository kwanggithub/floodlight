package net.floodlightcontroller.virtualnetwork;

import java.io.IOException;

import net.floodlightcontroller.util.MACAddress;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.MappingJsonFactory;
import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Put;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HostResource extends org.restlet.resource.ServerResource {
    protected static Logger log = LoggerFactory.getLogger(HostResource.class);
        
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
            else if (n.equals("attachment")) {
                while (jp.nextToken() != JsonToken.END_OBJECT) {
                    String field = jp.getCurrentName();
                    if (field.equals("id")) {
                        jp.nextToken();
                        host.hostId = jp.getText();
                    } else if (field.equals("mac")) {
                        jp.nextToken();
                        String m = jp.getText();
                        host.mac = MACAddress.valueOf(m);
                    }
                }
            }
        }
        
        jp.close();
    }
    
    @Put
    public String addHost(String postData) {
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        VirtualNetworkHost host = new VirtualNetworkHost();
        host.port = (String) getRequestAttributes().get("port");
        host.guid = (String) getRequestAttributes().get("network");
        host.tenantId = (String) getRequestAttributes().get("tenant");
        
        try {
            jsonToHost(postData, host);
        } catch (IOException e) {
            log.error("Could not parse JSON {}", e.getMessage());
        }
        vns.addHost(host);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
    
    
    @Delete
    public String deleteHost() {
        String port = (String) getRequestAttributes().get("port");
        String mac = (String) getRequestAttributes().get("mac");
        IVirtualNetworkService vns =
                (IVirtualNetworkService)getContext().getAttributes().
                    get(IVirtualNetworkService.class.getCanonicalName());
        if (mac != null)
            vns.deleteHost(MACAddress.valueOf(mac), port);
        else
            vns.deleteHost(null, port);
        setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
    }
}
