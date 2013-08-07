package org.projectfloodlight.packet;

import org.junit.Test;
import org.projectfloodlight.packet.IPacket;
import org.projectfloodlight.packet.IPv4;
import org.projectfloodlight.packet.PacketParsingException;
import org.projectfloodlight.packet.VRRP;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class VRRPTest {

    //IPv4 serialized packet.
    private byte[] pktSerialized1 = new byte[] {
            0x45, 0x00, 0x00, 0x24,
            0x34, 0x65, 0x00, 0x00,
            (byte)0xff, 0x70, 0x41, (byte)0xcd,
            0x0a, (byte)0xc0, 0x5a, 0x65,
            (byte)0xe0, 0x00, 0x00, 0x12,
            0x21, 0x64, 0x64, 0x00,
            0x00, 0x02, 0x7a, (byte)0x99,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
    };

    private byte[] pktSerialized2 = new byte[] {
            0x45, 0x10, 0x00, 0x38,
            (byte)0x8f, 0x7d, 0x40, 0x00,
            (byte)0xff, 0x70, (byte)0xf0, (byte)0xf2,
            0x0a, (byte)0xc0, 0x10, 0x03,
            0x30, 0x00, 0x00, 0x12,
            0x21, 0x02, 0x64, 0x07,
            (byte)0x00, (byte)0x01, (byte)0x9f, (byte)0x1e,
            (byte)0x8a, (byte)0x8e, (byte)0x60, (byte)0xfb,
            (byte)0xe3, (byte)0x34, (byte)0xbb, (byte)0xa9,
            (byte)0x61, (byte)0xf7, (byte)0x2e, (byte)0xd7,
            (byte)0x93, (byte)0x50, (byte)0x6f, (byte)0xe1,
            (byte)0xd7, (byte)0xd2, (byte)0x29, (byte)0x63,
            (byte)0xbd, (byte)0xb1, (byte)0x0f, (byte)0x10,
            (byte)0x3a, (byte)0x9e, (byte)0xb4, (byte)0xd7
    };

    // pktSerialized3 is just a serialized version of the
    // de-serialized pktSerialized2 packet.  The difference
    // is that the authentication data is added to the end
    // of the VRRP message.  Thus, the IP payload length
    // is increased by 8 bytes.

    /*
    private byte[] pktSerialized3 = new byte[] {
            0x45, 0x10, 0x00, 0x40,
            // last byte is 0x40 due to authentication data
            (byte)0x8f, 0x7d, 0x40, 0x00,
            (byte)0xff, 0x70, (byte)0xf0, (byte)0xf2,
            0x0a, (byte)0xc0, 0x10, 0x03,
            0x30, 0x00, 0x00, 0x12,
            0x21, 0x02, 0x64, 0x07,
            (byte)0x00, (byte)0x01, (byte)0x9f, (byte)0x1e,
            (byte)0x8a, (byte)0x8e, (byte)0x60, (byte)0xfb,
            (byte)0xe3, (byte)0x34, (byte)0xbb, (byte)0xa9,
            (byte)0x61, (byte)0xf7, (byte)0x2e, (byte)0xd7,
            (byte)0x93, (byte)0x50, (byte)0x6f, (byte)0xe1,
            (byte)0xd7, (byte)0xd2, (byte)0x29, (byte)0x63,
            (byte)0xbd, (byte)0xb1, (byte)0x0f, (byte)0x10,
            (byte)0x3a, (byte)0x9e, (byte)0xb4, (byte)0xd7,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00
    }; */

    @Test
    public void testSerialize1() {
        VRRP vrrp = new VRRP();
        vrrp.setVersion((byte)0x02);
        vrrp.setType((byte)0x01);
        vrrp.setVrid((byte)0x64);
        vrrp.setPriority((byte)0x64);
        vrrp.setPriority((byte)0x64);
        vrrp.setAdvertisementInterval((byte)0x02);

        IPacket packet = new IPv4()
        .setSourceAddress("10.192.90.101")
        .setDestinationAddress("224.0.0.18")
        .setDiffServ((byte)0x00)
        .setTtl((byte)0xff)
        .setIdentification((short)0x3465)
        .setFlags((byte)0x00)
        .setProtocol((byte)0x70)
        .setPayload(vrrp);

        byte[] actual = packet.serialize();

        assertTrue(Arrays.equals(pktSerialized1, actual));
    }

    @Test
    public void testDeserialize2() throws PacketParsingException {
        IPacket packet = new IPv4();
        packet.deserialize(pktSerialized2, 0, pktSerialized2.length);
        byte[] actual = packet.serialize();
        // Currently, IPv4 doesn't serialize into a VRRP packet.
        // If/When it does, the resultant serialization will add
        // 8 bytes of authentication data as part of the VRRP
        // data, even if the authenticationType is 0 (none).
        // In that case, the test packet will resemble pktSerialized3.
        assertTrue(Arrays.equals(pktSerialized2, actual));
    }
}
