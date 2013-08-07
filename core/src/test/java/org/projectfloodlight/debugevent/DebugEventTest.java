package org.projectfloodlight.debugevent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.debugevent.DebugEvent;
import org.projectfloodlight.debugevent.IEventUpdater;
import org.projectfloodlight.debugevent.IDebugEventService.DebugEventInfo;
import org.projectfloodlight.debugevent.IDebugEventService.EventColumn;
import org.projectfloodlight.debugevent.IDebugEventService.EventFieldType;
import org.projectfloodlight.debugevent.IDebugEventService.EventType;
import org.projectfloodlight.test.FloodlightTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugEventTest extends FloodlightTestCase {
    DebugEvent debugEvent;
    protected static Logger log = LoggerFactory.getLogger(DebugEventTest.class);

    @Override
    @Before
    public void setUp() throws Exception {
        debugEvent = new DebugEvent();

    }


    @Test
    public void testRegisterAndUpdateEvent() throws Exception {
        assertEquals(0, debugEvent.currentEvents.size());
        IEventUpdater<SwitchyEvent> event1 = null;
        IEventUpdater<PacketyEvent> event2 = null;
        event1 = debugEvent.registerEvent("dbgevtest", "switchevent",
                                           "switchtest", EventType.ALWAYS_LOG,
                                           SwitchyEvent.class, 100);
        event2 = debugEvent.registerEvent("dbgevtest", "pktinevent",
                                           "pktintest", EventType.ALWAYS_LOG,
                                           PacketyEvent.class, 100);

        assertEquals(2, debugEvent.currentEvents.size());
        assertTrue(null != debugEvent.moduleEvents.get("dbgevtest").
                                                     get("switchevent"));
        int eventId1 = debugEvent.moduleEvents.get("dbgevtest").
                                                     get("switchevent");
        assertTrue(null != debugEvent.moduleEvents.get("dbgevtest").
                                                     get("pktinevent"));
        int eventId2 = debugEvent.moduleEvents.get("dbgevtest").
                                                     get("pktinevent");
        assertEquals(true, debugEvent.containsModuleName("dbgevtest"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","switchevent"));
        assertEquals(true, debugEvent.containsModuleEventName("dbgevtest","pktinevent"));

        assertEquals(0, debugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(0, debugEvent.allEvents[eventId2].eventBuffer.size());

        // update is immediately flushed to global store
        event1.updateEventWithFlush(new SwitchyEvent(1L, "connected"));
        assertEquals(1, debugEvent.allEvents[eventId1].eventBuffer.size());

        // update is flushed only when flush is explicitly called
        event2.updateEventNoFlush(new PacketyEvent(1L, 24L));
        assertEquals(0, debugEvent.allEvents[eventId2].eventBuffer.size());

        debugEvent.flushEvents();
        assertEquals(1, debugEvent.allEvents[eventId1].eventBuffer.size());
        assertEquals(1, debugEvent.allEvents[eventId2].eventBuffer.size());

        DebugEventInfo de = debugEvent.getSingleEventHistory("dbgevtest","switchevent", 100);
        assertEquals(1, de.events.size());
        assertEquals(true, de.events.get(0).get("dpid").equals("00:00:00:00:00:00:00:01"));
        assertEquals(true, de.events.get(0).get("reason").equals("connected"));

        DebugEventInfo de2 = debugEvent.getSingleEventHistory("dbgevtest","pktinevent", 100);
        assertEquals(1, de2.events.size());
        assertEquals(true, de2.events.get(0).get("dpid").equals("00:00:00:00:00:00:00:01"));
        assertEquals(true, de2.events.get(0).get("srcMac").equals("00:00:00:00:00:18"));
    }

    public class SwitchyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "reason", description = EventFieldType.STRING)
        String reason;

        public SwitchyEvent(long dpid, String reason) {
            this.dpid = dpid;
            this.reason = reason;
        }
    }

    public class PacketyEvent {
        @EventColumn(name = "dpid", description = EventFieldType.DPID)
        long dpid;

        @EventColumn(name = "srcMac", description = EventFieldType.MAC)
        long mac;

        public PacketyEvent(long dpid, long mac) {
            this.dpid = dpid;
            this.mac = mac;
        }
    }

    public class IntEvent {
        @EventColumn(name = "index", description = EventFieldType.PRIMITIVE)
        int index;

        public IntEvent(int i) {
            this.index = i;
        }

        @Override
        public String toString() {
            return String.valueOf(index);
        }
    }

    @Test
    public void testEventCyclesWithFlush() throws Exception {
        IEventUpdater<IntEvent> ev = null;
        ev = debugEvent.registerEvent("test", "int",
                                      "just a test", EventType.ALWAYS_LOG,
                                      IntEvent.class, 20);
        for (int i=0; i<20; i++)
            ev.updateEventWithFlush(new IntEvent(i));
        int i=19;
        DebugEventInfo dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (Map<String, String> m : dei.events) {
            assertTrue(m.get("index").equals(String.valueOf(i)));
            i--;
        }
        for (int j= 500; j<550; j++)
            ev.updateEventWithFlush(new IntEvent(j));
        int k=549;
        dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (Map<String, String> m : dei.events) {
            //log.info("{}", m.get("index"));
            assertTrue(m.get("index").equals(String.valueOf(k)));
            k--;
        }
    }


    @Test
    public void testEventCyclesNoFlush() throws Exception {
        IEventUpdater<IntEvent> ev = null;
        ev = debugEvent.registerEvent("test", "int",
                                      "just a test", EventType.ALWAYS_LOG,
                                      IntEvent.class, 20);
        // flushes when local buffer fills up
        for (int i=0; i<20; i++)
            ev.updateEventNoFlush(new IntEvent(i));
        int i=19;
        DebugEventInfo dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (Map<String, String> m : dei.events) {
            assertTrue(m.get("index").equals(String.valueOf(i)));
            i--;
        }
        //log.info("done with first bunch");
        // flushes when local buffer fills up or when flushEvents is explicitly called
        for (int j= 500; j<550; j++) {
            ev.updateEventNoFlush(new IntEvent(j));
            if (j == 515) debugEvent.flushEvents();
        }
        debugEvent.flushEvents();

        int k=549;
        dei = debugEvent.getSingleEventHistory("test","int", 100);
        for (Map<String, String> m : dei.events) {
            //log.info("{}", m.get("index"));
            assertTrue(m.get("index").equals(String.valueOf(k)));
            k--;
        }
    }
}
