/**
*    Clemson University OpenFlowDevelopment Team
*    Originally created by Scott Groel & Benjamin Ujcich, Clemson University.
*
*	 IPv6 was created and modeled after IPv4.java originally created and copyrighted
*	 by Big Switch Networks, and created by David Erickson, Stanford University.
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


package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.net.Inet6Address;

/**
 * @author Scott Groel, Clemson University
 *
 */
public class IPv6 extends BasePacket {
    public static final byte PROTOCOL_ICMPv6 = 0x3a;
    public static final byte PROTOCOL_TCP = 0x6;
    public static final byte PROTOCOL_UDP = 0x11;
    public static Map<Byte, Class<? extends IPacket>> protocolClassMap;

    static {
        protocolClassMap = new HashMap<Byte, Class<? extends IPacket>>();
//        protocolClassMap.put(PROTOCOL_ICMPv6, ICMPv6.class);
        protocolClassMap.put(PROTOCOL_TCP, TCP.class);
        protocolClassMap.put(PROTOCOL_UDP, UDP.class);
    }

    protected byte version;
    protected byte trafficClass;
    protected int flowLabel;
    protected short payloadLength;
    protected byte nextHeader;
    protected byte hopLimit;
    protected int[] sourceAddress;
    protected int[] destinationAddress;
    
    protected boolean isTruncated;

    /**
     * Default constructor that sets the version to 6.
     */
    public IPv6() {
        super();
        this.version = 6;
        this.sourceAddress = new int[4];
        this.destinationAddress = new int[4];
        isTruncated = false;
    }

    /**
     * @return the version
     */
    public byte getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public IPv6 setVersion(byte version) {
        this.version = version;
        return this;
    }

    /**
     * @return the sourceAddress
     */
    public int[] getSourceAddress() {
        return sourceAddress;
    }

    /**
     * @param sourceAddress the sourceAddress to set
     */
    public IPv6 setSourceAddress(int[] sourceAddress) {
        this.sourceAddress = sourceAddress;
        return this;
    }

    /**
     * @param sourceAddress the sourceAddress to set
     */
    public IPv6 setSourceAddress(String sourceAddress) {
        this.sourceAddress = IPv6.toIPv6Address(sourceAddress);
        return this;
    }

    private static int[] toIPv6Address(String sourceAddress2) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
     * @return the destinationAddress
     */
    public int[] getDestinationAddress() {
        return destinationAddress;
    }

    /**
     * @param destinationAddress the destinationAddress to set
     */
    public IPv6 setDestinationAddress(int[] destinationAddress) {
        this.destinationAddress = destinationAddress;
        return this;
    }

    /**
     * @param destinationAddress the destinationAddress to set
     */
    public IPv6 setDestinationAddress(String destinationAddress) {
        this.destinationAddress = IPv6.toIPv6Address(destinationAddress);
        return this;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        byte bscratch1, bscratch2;
        short sscratch1;
        
        bscratch1 = bb.get();
        this.version = (byte) ((bscratch1 >> 4) & 0xf);
        bscratch2 = bb.get();
        this.trafficClass = (byte) (((bscratch1 & 0xf) << 4) | ((bscratch2 >> 4) & 0xf));
        sscratch1 = bb.getShort();
        this.flowLabel = ((bscratch2 & 0xf) << 20) | sscratch1;
        this.payloadLength = bb.getShort();
        this.nextHeader = bb.get();
        this.hopLimit = bb.get();
        this.sourceAddress[0] = bb.getInt();
        this.sourceAddress[1] = bb.getInt();
        this.sourceAddress[2] = bb.getInt();
        this.sourceAddress[3] = bb.getInt();
        this.destinationAddress[0] = bb.getInt();
        this.destinationAddress[1] = bb.getInt();
        this.destinationAddress[2] = bb.getInt();
        this.destinationAddress[3] = bb.getInt();                

        IPacket payload;
        do {
        	if (IPv6.protocolClassMap.containsKey(this.nextHeader)) {
        		Class<? extends IPacket> clazz = IPv6.protocolClassMap.get(this.nextHeader);
        		try {
        			payload = clazz.newInstance();
	            } catch (Exception e) {
	                throw new RuntimeException("Error parsing payload for IPv6 packet", e);
	            }
	        } else {
	        	if (this.nextHeader != 0 && this.nextHeader != 58)
	        		continue;
	        	else
	        		payload = new Data();
	        }
	        this.payload = payload.deserialize(data, bb.position(), this.payloadLength);
	        this.payload.setParent(this);
	        break;
        } while (true);
        
        return this;
    }

	@Override
	public byte[] serialize() {
		// TODO Auto-generated method stub
		return null;
	}

   
}
