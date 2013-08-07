/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package org.projectfloodlight.topology.bigdb;

import org.projectfloodlight.core.bigdb.serializers.DPIDDataNodeSerializer;
import org.projectfloodlight.core.types.PortInterfacePair;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBSerialize;
import org.projectfloodlight.linkdiscovery.ILinkDiscovery.LinkDirection;
import org.projectfloodlight.linkdiscovery.ILinkDiscovery.LinkType;

/**
 * This class is both the datastructure and the serializer
 * for a link with the corresponding type of link.
 */
public class LinkWithType {    
    private SwitchInterface src;
    private SwitchInterface dst;
    private LinkType type;
    private LinkDirection direction;

    public LinkWithType(SwitchInterface src, SwitchInterface dst,
                        LinkType type, LinkDirection direction) {
        super();
        this.src = src;
        this.dst = dst;
        this.type = type;
        this.direction = direction;
    }

    @BigDBProperty("src")
    public SwitchInterface getSrc() {
        return src;
    }

    @BigDBProperty("dst")
    public SwitchInterface getDst() {
        return dst;
    }
    
    @BigDBProperty("link-type")
    public LinkType getType() {
        return type;
    }

    @BigDBProperty("link-direction")
    public LinkDirection getDirection() {
        return direction;
    }
    
    public static class SwitchInterface {
        private long switchDpid;
        private PortInterfacePair iface;

        public SwitchInterface(long switchDpid, PortInterfacePair iface) {
            super();
            this.switchDpid = switchDpid;
            this.iface = iface;
        }
        
        @BigDBProperty("switch-dpid")
        @BigDBSerialize(using=DPIDDataNodeSerializer.class)
        public long getSwitchDpid() {
            return switchDpid;
        }
        @BigDBProperty("interface")
        public PortInterfacePair getIface() {
            return iface;
        }
    }
}