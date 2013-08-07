package org.projectfloodlight.core.bigdb.serializers;


import java.util.Map.Entry;
import java.util.Set;

import org.openflow.util.HexString;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.data.DataNodeGenerator;
import org.projectfloodlight.db.data.DataNodeSerializer;
import org.projectfloodlight.routing.Link;
import org.projectfloodlight.topology.Cluster;

public class ClusterSerializer implements DataNodeSerializer<Cluster> {
    @Override
    public void serialize(Cluster c, DataNodeGenerator gen) throws BigDBException {
        gen.writeMapStart();
        gen.writeStringField("switch-dpid", HexString.toHexString(c.getId()));
        gen.writeListFieldStart("node-link");
        for (Entry<Long, Set<Link>> e : c.getLinks().entrySet()) {
            gen.writeMapStart();
            gen.writeStringField("switch-dpid", HexString.toHexString(e.getKey()));
            gen.writeListFieldStart("link");
            for (Link l : e.getValue()) {
                gen.writeMapStart();
                gen.writeMapFieldStart("src");
                gen.writeStringField("switch-dpid", HexString.toHexString(l.getSrc()));
                gen.writeNumberField("port", l.getSrcPort());
                gen.writeMapEnd();
                gen.writeMapFieldStart("dst");
                gen.writeStringField("switch-dpid", HexString.toHexString(l.getDst()));
                gen.writeNumberField("port", l.getDstPort());
                gen.writeMapEnd();
                gen.writeMapEnd();
            }
            gen.writeListEnd();
            gen.writeMapEnd();
        }
        gen.writeListEnd();
        gen.writeMapEnd();
    }

}
