/**
 *    Copyright 2011,2012 Big Switch Networks, Inc.
 *    Originally created by David Erickson, Stanford University
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

/**
 * @author Srini
 */

package net.floodlightcontroller.device.internal;

import net.bigdb.data.annotation.BigDBIgnore;
import net.bigdb.data.annotation.BigDBProperty;
import net.bigdb.data.annotation.BigDBSerialize;
import net.bigdb.data.serializers.EnumDataNodeSerializer;
import net.bigdb.data.serializers.ISOLongDateDataNodeSerializer;
import net.floodlightcontroller.core.bigdb.serializers.DPIDDataNodeSerializer;
import net.floodlightcontroller.device.SwitchPort;

import org.openflow.util.HexString;

public class AttachmentPoint {
    long  sw;
    short port;
    long  activeSince;
    long  lastSeen;

    String interfaceName; // To serve the rest API
    SwitchPort.ErrorStatus status;

    // Timeout for moving attachment points from OF/broadcast
    // domain to another.
    public static final long INACTIVITY_INTERVAL = 30000; // 30 seconds
    public static final long EXTERNAL_TO_EXTERNAL_TIMEOUT = 5000;  // 5 seconds
    public static final long OPENFLOW_TO_EXTERNAL_TIMEOUT = 30000; // 30 seconds
    public static final long CONSISTENT_TIMEOUT = 30000;           // 30 seconds

    /**
     * Only used to construct a attachmentPoint object for searching.
     * 
     * It is only construct the key fields of the attacmentPoint.
     * 
     * @param sw
     * @param port
     * @param lastSeen
     */
    public AttachmentPoint(long sw, short port, 
                           long lastSeen) {
        this.sw = sw;
        this.port = port;
        this.lastSeen = lastSeen;
    }

    public AttachmentPoint(long sw, short port, long lastSeen,
                           String interfaceName) {
        this.sw = sw;
        this.port = port;
        this.lastSeen = lastSeen;
        this.interfaceName = interfaceName;
        this.activeSince = lastSeen;
    }

    @BigDBProperty(value = "dpid")
    @BigDBSerialize(using=DPIDDataNodeSerializer.class)
    public long getSw() {
        return sw;
    }
    public void setSw(long sw) {
        this.sw = sw;
    }
    
    @BigDBIgnore
    public short getPort() {
        return port;
    }
    public void setPort(short port) {
        this.port = port;
    }
    
    @BigDBIgnore
    public long getActiveSince() {
        return activeSince;
    }
    public void setActiveSince(long activeSince) {
        this.activeSince = activeSince;
    }

    @BigDBProperty(value="last-seen")
    @BigDBSerialize(using=ISOLongDateDataNodeSerializer.class)
    public long getLastSeen() {
        return lastSeen;
    }
    public void setLastSeen(long lastSeen) {
        if (this.lastSeen + INACTIVITY_INTERVAL < lastSeen)
            this.activeSince = lastSeen;
        if (this.lastSeen < lastSeen)
            this.lastSeen = lastSeen;
    }
    
    @BigDBProperty(value = "error-status")
    @BigDBSerialize(using=EnumDataNodeSerializer.class)
    public SwitchPort.ErrorStatus getStatus() {
        return status;
    }

    public void setStatus(SwitchPort.ErrorStatus status) {
        this.status = status;
    }
    
    @BigDBProperty(value = "interface-name")
    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    /**
     *  Hash is generated using only switch and port
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + port;
        result = prime * result + (int) (sw ^ (sw >>> 32));
        return result;
    }

    /**
     * Compares only the switch and port
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AttachmentPoint other = (AttachmentPoint) obj;
        if (port != other.port)
            return false;
        if (sw != other.sw)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "AttachmentPoint [sw=" + HexString.toHexString(sw) + ", port=" + port
               + ", activeSince=" + activeSince + ", lastSeen=" + lastSeen
               + "]";
    }
}
