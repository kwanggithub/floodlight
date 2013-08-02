package net.floodlightcontroller.core.types;

import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBSerialize;
import net.bigdb.data.serializers.LongToHexStringSerializer;

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
