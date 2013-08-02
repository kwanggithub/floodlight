package org.openflow.protocol.action;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Represents a DROP OFAction. Note that this class should only
 * be used for schema purposes. OpenFlow defines a drop as an empty
 * action list in a FlowMod. This should NOT be added to a FlowMod.
 * @author alexreimers
 *
 */
public class OFActionDrop extends OFAction {
    
    public OFActionDrop() {
        super.setType(OFActionType.DROP);
    }
    
    @Override
    public void writeTo(ChannelBuffer data) {
        // no-op
    }

    @Override
    public int hashCode() {
        final int prime = 449;
        int result = super.hashCode();
        result = prime * result;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
