package org.projectfloodlight.core.internal;

import static org.easymock.EasyMock.anyLong;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.projectfloodlight.core.internal.OFStatisticsFuture;
import org.projectfloodlight.core.internal.OFSwitchImpl;

/**
 * A sublcass of OFSwitchImpl that contains extra setters.
 * This class can be used for testing.
 * @author alexreimers
 *
 */
public class MockOFSwitchImpl extends OFSwitchImpl {
    protected Map<OFStatisticsType, List<OFStatistics>> statsMap;
    
    public MockOFSwitchImpl() {
        super();
        statsMap = new HashMap<OFStatisticsType, List<OFStatistics>>();
    }
    
    public void setConnectedSince(Date d) {
        this.connectedSince = d;
        setConnected(true);
    }
    
    public void setId(long id) {
        this.datapathId = id;
        this.stringId = HexString.toHexString(id);
    }
    
    public void setBuffers(int buffers) {
        this.buffers = buffers;
    }
    
    public void setCapabilities(int cap) {
        this.capabilities = cap;
    }
    
    @Override
    public SocketAddress getInetAddress() {
        SocketAddress socketAddress = null;
        try {
            byte[] addressBytes = {1, 1, 1, (byte)(this.datapathId%255)};
            InetAddress inetAddress = InetAddress.getByAddress(addressBytes);
            socketAddress = new InetSocketAddress(inetAddress, 7847);
        }
        catch (Exception e) {
        }
        return socketAddress;
    }
    
    public void setAttributes(Map<Object, Object> attrs) {
        this.attributes.putAll(attrs);
    }

    @Override
    public Future<List<OFStatistics>> 
        queryStatistics(OFStatisticsRequest request) throws IOException {
        Future<List<OFStatistics>> ofStatsFuture = 
                createNiceMock(OFStatisticsFuture.class);

        // We create a mock future and return info from the map
        try {
            expect(ofStatsFuture.get(anyLong(), anyObject(TimeUnit.class))).
            andReturn(statsMap.get(request.getStatisticType())).anyTimes();
            replay(ofStatsFuture);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return ofStatsFuture;
    }
    
    public void addStatsRequest(OFStatisticsType type, List<OFStatistics> reply) {
        statsMap.put(type, reply);
    }
}
