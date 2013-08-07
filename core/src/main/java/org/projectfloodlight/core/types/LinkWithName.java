package org.projectfloodlight.core.types;

import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.db.data.serializers.LongToHexStringSerializer;

public class LinkWithName {
    Long srcDpid;
    String srcInterface;
    Long dstDpid;
    String dstInterface;
    
    public LinkWithName(Long srcDpid, String srcInterface, Long dstDpid,
            String dstInterface) {
        super();
        this.srcDpid = srcDpid;
        this.srcInterface = srcInterface;
        this.dstDpid = dstDpid;
        this.dstInterface = dstInterface;
    }

    @BigDBProperty(value="src-switch")
    @BigDBSerialize(using = LongToHexStringSerializer.class)
    public Long getSrcDpid() {
        return srcDpid;
    }

    @BigDBProperty(value="src-interface")
    public String getSrcInterface() {
        return srcInterface;
    }

    @BigDBProperty(value="dst-switch")
    @BigDBSerialize(using = LongToHexStringSerializer.class)
    public Long getDstDpid() {
        return dstDpid;
    }

    @BigDBProperty(value="dst-interface")
    public String getDstInterface() {
        return dstInterface;
    }
}
