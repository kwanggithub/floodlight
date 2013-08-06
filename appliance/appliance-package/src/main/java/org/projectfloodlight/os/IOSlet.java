package org.projectfloodlight.os;

import java.util.EnumSet;

public interface IOSlet<K extends Enum<K>> {
    /**
     * Get the list of types that are provided by this object.  The object 
     * will be notified when the appropriate configuration has been changed 
     * and will be expected to apply the appropriate changes to the underlying
     * OS
     * @return
     */
    public EnumSet<K> provides();
}
