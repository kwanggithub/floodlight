package net.floodlightcontroller.core.bigdb.serializers;


import java.util.Map.Entry;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.data.DataNodeGenerator;
import net.bigdb.data.DataNodeSerializer;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.Cluster;

import org.openflow.util.HexString;

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
