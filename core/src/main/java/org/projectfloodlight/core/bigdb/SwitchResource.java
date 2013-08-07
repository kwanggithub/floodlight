package org.projectfloodlight.core.bigdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionDrop;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFInterfaceStatisticsReply;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFQueueStatisticsReply;
import org.openflow.protocol.statistics.OFQueueWithInterfaceStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.SwitchStatistics;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.annotation.BigDBParam;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBQuery;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.util.FilterIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchResource extends FloodlightResource {
    protected static final Logger log = 
            LoggerFactory.getLogger(SwitchResource.class);

    @BigDBQuery
    public static Iterator<IOFSwitch> getSwitches(
            @BigDBParam("query") Query query) throws BigDBException {

        Step switchStep = query.getSteps().get(0);
        String exactMatchDpid = (String) switchStep.getExactMatchPredicateValue("dpid");
        if (exactMatchDpid != null) {
            try {
                Long dpidInt = HexString.toLong(exactMatchDpid);
                IOFSwitch sw = getFloodlightProvider().getSwitch(dpidInt);
                return (sw != null) ? Collections.singleton(sw).iterator() :
                    Collections.<IOFSwitch>emptySet().iterator();
            }
            catch (Exception e) {
                throw new BigDBException("Invalid switch DPID");
            }
        }
        Iterator<IOFSwitch> switchIter = 
                getFloodlightProvider().getAllSwitchMap().values().iterator();
        final String prefixMatchDpid = switchStep.getPrefixMatchPredicateString("dpid");
        if (prefixMatchDpid != null) {
            return new FilterIterator<IOFSwitch>(switchIter) {
                @Override
                protected boolean matches(IOFSwitch value) {
                    return value.getStringId().startsWith(prefixMatchDpid);
                }
            };
        }
        return switchIter;
    }

    private static String getDpidString(Step switchStep) throws BigDBException {
        String dpid = (String) switchStep.getExactMatchPredicateValue("dpid");
        if (dpid == null) {
            throw new BigDBException("The \"switch\" step in the query must " +
                    "have an exact match predicate for a specific dpid");
        }
        return dpid;
    }

    protected static Object getOneSwitchStats(String dpid, OFStatisticsType statsType) {

        List<OFStatistics> ofStats = SwitchStatistics.getSwitchStatistics(
                dpid, statsType, getFloodlightProvider(), log);

        Object result = null;

        switch (statsType) {
            case VENDOR:
            case DESC:
            case AGGREGATE:
                if (ofStats != null)
                    result = ofStats.get(0);
                break;
            case PORT:
            case FLOW:
            case QUEUE:
                result = addInformationToStats(statsType, ofStats, dpid);
                break;
            case TABLE:
                result = ofStats;
                break;
            default:
                break;
        }

        return result;
    }

    private static Object getStats(Query query, OFStatisticsType sType)
        throws BigDBException {

        assert query != null;
        assert sType != null;

        Step switchStep = query.getStep(0);
        String dpid = getDpidString(switchStep);

        Object statsObject = getOneSwitchStats(dpid, sType);
        if (statsObject == null) {
            throw new BigDBException(String.format(
                    "Switch %s is not connected", dpid));
        }

        return statsObject;
    }

    /**
     * Adds extra information to the statistics replies
     * @param flows
     * @return A list of OFStatistics with modified
     */
    private static List<OFStatistics> addInformationToStats(
            OFStatisticsType type, List<OFStatistics> stats, String dpid) {
        long swId = HexString.toLong(dpid);
        List<OFStatistics> nList = new ArrayList<OFStatistics>();
        if (stats == null)
            return null;
        for (OFStatistics ofs : stats) {
            if (ofs instanceof OFFlowStatisticsReply) {
                OFFlowStatisticsReply ofFlowReply = (OFFlowStatisticsReply) ofs;
                if ((ofFlowReply.getActions() == null) ||
                        (ofFlowReply.getActions().size() == 0)) {
                    List<OFAction> ofActionList = new ArrayList<OFAction>(1);
                    ofActionList.add(new OFActionDrop());
                    ofFlowReply.setActions(ofActionList);
                }
            } else if (ofs instanceof OFPortStatisticsReply) {
                OFPortStatisticsReply ofpr = (OFPortStatisticsReply) ofs;
                String intfName = getFloodlightProvider().getSwitch(swId).
                        getPort(ofpr.getPortNumber()).getName();
                nList.add(new OFInterfaceStatisticsReply(ofpr, intfName));
            } else if (ofs instanceof OFQueueStatisticsReply) {
                OFQueueStatisticsReply ofqr = (OFQueueStatisticsReply) ofs;
                String intfName = getFloodlightProvider().getSwitch(swId).
                        getPort(ofqr.getPortNumber()).getName();
                nList.add(new OFQueueWithInterfaceStatisticsReply(ofqr, intfName));
            }
        }

        if (type == OFStatisticsType.FLOW) {
            return stats;
        }

        return nList;
    }

    @BigDBQuery
    @BigDBPath("stats/flow")
    public static Object getFlowStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.FLOW);
    }

    @BigDBQuery
    @BigDBPath("stats/aggregate")
    public static Object getAggregateStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.AGGREGATE);
    }

    @BigDBQuery
    @BigDBPath("stats/interface")
    public static Object getInterfaceStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.PORT);
    }

    @BigDBQuery
    @BigDBPath("stats/queue")
    public static Object getQueueStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.QUEUE);
    }

    @BigDBQuery
    @BigDBPath("stats/desc")
    public static Object getDescStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.DESC);
    }

    @BigDBQuery
    @BigDBPath("stats/table")
    public static Object getTableStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.TABLE);
    }

    // FIXME: andiw: stats/vendor is not currently defined in the schema.
    // Unclear what it is useful for.
    //    @BigDBQuery
    //    @BigDBPath("stats/vendor")
    public static Object getVendorStats(@BigDBParam("query") Query query)
            throws BigDBException {
        return getStats(query, OFStatisticsType.VENDOR);
    }
}
