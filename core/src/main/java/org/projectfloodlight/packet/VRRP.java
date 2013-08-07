/**
*    Copyright 2011, Big Switch Networks, Inc.
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

package org.projectfloodlight.packet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


/**
 * Implements VRRP packet format
 * @author srini@bigswitch.com
 */
public class VRRP extends BasePacket {
    protected byte version; // 4 bits
    protected byte type;  //4 bits
    protected byte vrid;
    protected byte priority;
    protected byte ipcount;
    protected byte authenticationType;
    protected byte advertisementInterval;
    protected short checksum;
    protected List<Integer> ipAddressList;
    protected long authenticationData; // 8 bytes of data

    public VRRP () {
        version = 0x0;
        type = 0x0;
        vrid = 0x00;
        priority = (byte)0xFF;
        ipcount = 0x00;
        authenticationType = 0x00;
        advertisementInterval = 0x00;
        checksum = 0x0000;
        ipAddressList = new ArrayList<Integer>();
        authenticationData = 0L;
    }


    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public byte getVrid() {
        return vrid;
    }

    public void setVrid(byte vrid) {
        this.vrid = vrid;
    }

    public byte getPriority() {
        return priority;
    }

    public void setPriority(byte priority) {
        this.priority = priority;
    }

    public byte getIpcount() {
        return ipcount;
    }

    public void setIpcount(byte ipcount) {
        this.ipcount = ipcount;
    }

    public byte getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(byte authenticationType) {
        this.authenticationType = authenticationType;
    }

    public byte getAdvertisementInterval() {
        return advertisementInterval;
    }

    public void setAdvertisementInterval(byte advertisementInterval) {
        this.advertisementInterval = advertisementInterval;
    }

    public List<Integer> getIpAddressList() {
        return ipAddressList;
    }

    public void setIpAddressList(List<Integer> ipAddressList) {
        this.ipcount = (byte)ipAddressList.size();
        this.ipAddressList = ipAddressList;
    }

    public Long getAuthenticationData() {
        return authenticationData;
    }

    public void setAuthenticationData(Long authenticationData) {
        this.authenticationData = authenticationData;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + advertisementInterval;
        result = prime * result
                + (int) (authenticationData ^ (authenticationData >>> 32));
        result = prime * result + authenticationType;
        result = prime * result + checksum;
        result = prime * result
                + ((ipAddressList == null) ? 0 : ipAddressList.hashCode());
        result = prime * result + ipcount;
        result = prime * result + priority;
        result = prime * result + type;
        result = prime * result + version;
        result = prime * result + vrid;
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        VRRP other = (VRRP) obj;
        if (advertisementInterval != other.advertisementInterval)
            return false;
        if (authenticationData != other.authenticationData)
            return false;
        if (authenticationType != other.authenticationType)
            return false;
        if (checksum != other.checksum)
            return false;
        if (ipAddressList == null) {
            if (other.ipAddressList != null)
                return false;
        } else if (!ipAddressList.equals(other.ipAddressList))
            return false;
        if (ipcount != other.ipcount)
            return false;
        if (priority != other.priority)
            return false;
        if (type != other.type)
            return false;
        if (version != other.version)
            return false;
        if (vrid != other.vrid)
            return false;
        return true;
    }



    /**
     * Serializes the packet. Will compute and set the following fields if they
     * are set to specific values at the time serialize is called:
     *      -checksum : 0
     */
    @Override
    public byte[] serialize() {
        // length of the packet in bytes
        int length = (ipAddressList.size() * 4) + 16;

        byte[] data = new byte[length];
        ByteBuffer bb = ByteBuffer.wrap(data);


        byte vt = (byte) (version << 4 | type);
        bb.put(vt);
        bb.put(vrid);
        bb.put(priority);
        bb.put(ipcount);
        bb.put(authenticationType);
        bb.put(advertisementInterval);
        bb.putShort(checksum);
        for(short j=0; j<(short)ipcount; ++j) {
            bb.putInt(ipAddressList.get(j));
        }
        bb.putLong(authenticationData);

        if (checksum == 0) {
            // compute checksum and put it back.
            int accumulation = 0;
            bb.rewind();
            for (int i = 0; i < length / 2; ++i) {
                accumulation += 0xffff & bb.getShort();
            }
            accumulation = ((accumulation >> 16) & 0xffff)
                    + (accumulation & 0xffff);
            this.checksum = (short) (~accumulation & 0xffff);
            bb.putShort(6, this.checksum);
        }
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length)
            throws PacketParsingException {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        byte vt = bb.get();
        type = (byte)(vt & 0x0f);
        version = (byte)(vt >> 4);
        vrid = bb.get();
        priority = bb.get();
        ipcount = bb.get();
        authenticationType = bb.get();
        advertisementInterval = bb.get();
        checksum = bb.getShort();

        short count = (short) ipcount;
        for(short i=0; i<count; ++i) {
            ipAddressList.add(bb.getInt());
        }

        if (authenticationType != 0x00) {
            authenticationData = bb.getLong();
        } else {
            authenticationData = 0x00;
        }

        return this;
    }
}
