package net.floodlightcontroller.core;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.annotations.LogMessageDoc;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.slf4j.Logger;

public class SwitchStatistics {
    /**
     * Retrieves switch statistics. Note that this method blocks for a maximum
     * of 10 seconds. If a reply is not heard within 10 seconds null is returned.
     * 
     * @param switchId The DPID of the switch. 
     * @param statType The OFStatisticsType to get.
     * @param floodlightProvider The IFloodlightProvider
     * @param log A reference to the logger.
     * @return List of OFStatistics, or null if there was an error.
     */
    @LogMessageDoc(level="ERROR",
            message="Failure retrieving statistics from switch {switch}",
            explanation="An error occurred while retrieving statistics" +
                 "from the switch",
            recommendation=LogMessageDoc.CHECK_SWITCH + " " +
                 LogMessageDoc.GENERIC_ACTION)
    public static List<OFStatistics> getSwitchStatistics(long switchId, OFStatisticsType statType, 
            IFloodlightProviderService floodlightProvider, Logger log) {
        
        log.debug("Sending request to switch {} for stat type {}",
                HexString.toHexString(switchId), statType);
        IOFSwitch sw = floodlightProvider.getSwitch(switchId);
        Future<List<OFStatistics>> future;
        List<OFStatistics> values = null;
        if (sw != null) {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(statType);
            int requestLength = req.getLengthU();
            if (statType == OFStatisticsType.FLOW) {
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.AGGREGATE) {
                OFAggregateStatisticsRequest specificReq = new OFAggregateStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.PORT) {
                OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_NONE.getValue());
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.QUEUE) {
                OFQueueStatisticsRequest specificReq = new OFQueueStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_ALL.getValue());
                // LOOK! openflowj does not define OFPQ_ALL! pulled this from openflow.h
                // note that I haven't seen this work yet though...
                specificReq.setQueueId(0xffffffff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.DESC ||
                       statType == OFStatisticsType.TABLE) {
                // pass - nothing todo besides set the type above
            }
            
            req.setLengthU(requestLength);
            try {
                future = sw.queryStatistics(req);
                values = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch " + sw, e);
            }
        } else {
            log.warn("Could not send stats request to nonexistent switch {}",
                    HexString.toHexString(switchId));
        }
        return values;
    }
    
    /**
     * Retrieves switch statistics. Note that this method blocks for a maximum
     * of 10 seconds. If a reply is not heard within 10 seconds null is returned.
     * 
     * @param switchId The DPID of the switch. 
     * @param statType The OFStatisticsType to get.
     * @param floodlightProvider The IFloodlightProvider
     * @param log A reference to the logger.
     * @return List of OFStatistics, or null if there was an error.
     */
    public static List<OFStatistics> getSwitchStatistics(String switchId, OFStatisticsType statType,
            IFloodlightProviderService floodlightProvider, Logger log) {
        return getSwitchStatistics(HexString.toLong(switchId), statType, 
                floodlightProvider, log);
    }
}
