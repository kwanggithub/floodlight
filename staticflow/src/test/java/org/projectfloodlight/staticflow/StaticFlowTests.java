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

package org.projectfloodlight.staticflow;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.bigdb.IBigDBService;
import net.floodlightcontroller.bigdb.MockBigDBService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.test.MockFloodlightProvider;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.Test;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.projectfloodlight.staticflow.StaticFlowEntry;
import org.projectfloodlight.staticflow.StaticFlowEntryPusher;

public class StaticFlowTests extends FloodlightTestCase {

    private static String TestSwitch1DPID = "00:00:00:00:00:00:00:01";
    private final int TotalTestRules = 3;
    private StaticFlowEntry testRule1, testRule2, testRule3;

    protected void initTestRules() {
        OFFlowMod FlowMod1 = new OFFlowMod();
        // setup match
        OFMatch match = new OFMatch();
        match.fromString("dl-dst=00:20:30:40:50:60");
        // Use ArrayList instead of LinkedList because that's what the
        // default action factory uses and the assertEquals doesn't work if
        // the two flow mods use different list implementations. Probably would
        // be better to make verifyActions work correctly independent of the
        // list implementation in the
        List<OFAction> actions = new LinkedList<OFAction>();
        actions.add(new OFActionOutput((short)1, Short.MAX_VALUE));

        FlowMod1.setMatch(match);
        FlowMod1.setActions(actions);
        FlowMod1.setBufferId(-1);
        FlowMod1.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod1.setPriority(Short.MAX_VALUE);
        FlowMod1.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions

        testRule1 = new StaticFlowEntry("testRule1", TestSwitch1DPID, FlowMod1, true);

        OFFlowMod FlowMod2 = new OFFlowMod();
        // setup match
        match = new OFMatch();
        match.fromString("nw-dst=192.168.1.0/24");
        // setup actions
        actions = new LinkedList<OFAction>();
        actions.add(new OFActionOutput((short)1, Short.MAX_VALUE));
        // done
        FlowMod2.setMatch(match);
        FlowMod2.setActions(actions);
        FlowMod2.setBufferId(-1);
        FlowMod2.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod2.setPriority(Short.MAX_VALUE);
        FlowMod2.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions
        testRule2 = new StaticFlowEntry("testRule2", TestSwitch1DPID, FlowMod2, true);

        OFFlowMod FlowMod3 = new OFFlowMod();
        // setup match
        match = new OFMatch();
        match.fromString("dl-dst=00:20:30:40:50:60,dl-vlan=4096");
        // setup actions
        actions = new LinkedList<OFAction>();
        actions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue(), Short.MAX_VALUE));
        // done
        FlowMod3.setMatch(match);
        FlowMod3.setActions(actions);
        FlowMod3.setBufferId(-1);
        FlowMod3.setOutPort(OFPort.OFPP_NONE.getValue());
        FlowMod3.setPriority(Short.MAX_VALUE);
        FlowMod3.setLengthU(OFFlowMod.MINIMUM_LENGTH + 8);  // 8 bytes of actions
        testRule3 = new StaticFlowEntry("testRule3", TestSwitch1DPID, FlowMod3, true);
    }

    private void verifyFlowMod(OFFlowMod testFlowMod,
            OFFlowMod goodFlowMod) {
        verifyMatch(testFlowMod, goodFlowMod);
        verifyActions(testFlowMod, goodFlowMod);
        // dont' bother testing the cookie; just copy it over
        goodFlowMod.setCookie(testFlowMod.getCookie());
        // .. so we can continue to use .equals()
        assertEquals(goodFlowMod, testFlowMod);
    }

    private void verifyMatch(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
        assertEquals(goodFlowMod.getMatch(), testFlowMod.getMatch());
    }

    private void verifyActions(OFFlowMod testFlowMod, OFFlowMod goodFlowMod) {
        List<OFAction> goodActions = goodFlowMod.getActions();
        List<OFAction> testActions = testFlowMod.getActions();
        assertNotNull(goodActions);
        assertNotNull(testActions);
        assertEquals(goodActions.size(), testActions.size());
        // assumes actions are marshalled in same order; should be safe
        for(int i = 0; i < goodActions.size(); i++) {
            assertEquals(goodActions.get(i), testActions.get(i));
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        initTestRules();
    }

    @Test
    public void testStaticFlowPushAndDelete() throws Exception {
        StaticFlowEntryPusher staticFlowEntryPusher = new StaticFlowEntryPusher();
        long dpid = HexString.toLong(TestSwitch1DPID);

        // Create a Switch and attach a switch
        IOFSwitch mockSwitch = createNiceMock(IOFSwitch.class);
        Capture<FloodlightContext> contextCapture = new Capture<FloodlightContext>(CaptureType.ALL);
        Capture<List<OFMessage>> writeCaptureList = new Capture<List<OFMessage>>(CaptureType.ALL);

        mockSwitch.write(capture(writeCaptureList), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.flush();
        expectLastCall().anyTimes();

        FloodlightModuleContext fmc = new FloodlightModuleContext();
        MockFloodlightProvider mockFloodlightProvider = getMockFloodlightProvider();
        Map<Long, IOFSwitch> switchMap = new HashMap<Long, IOFSwitch>();
        switchMap.put(dpid, mockSwitch);
        mockFloodlightProvider.setSwitches(switchMap);
        staticFlowEntryPusher.floodlightProvider = mockFloodlightProvider;
        MockBigDBService bigdb = new MockBigDBService();
        bigdb.addModuleSchema("floodlight", "2012-10-22");
        bigdb.addModuleSchema("static-flow-pusher", "2012-11-21");

        fmc.addService(IBigDBService.class, bigdb);
        fmc.addService(IFloodlightProviderService.class, mockFloodlightProvider);
        bigdb.init(fmc);
        staticFlowEntryPusher.init(fmc);
        
        staticFlowEntryPusher.bigDBService = bigdb;
        staticFlowEntryPusher.startUp(fmc);    // again, to hack unittest

        EasyMock.expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        EasyMock.expect(mockSwitch.getStringId()).andReturn(TestSwitch1DPID).anyTimes();
        EasyMock.replay(mockSwitch);

        staticFlowEntryPusher.addFlow(testRule1.getName(), testRule1.getFlowMod(), testRule1.getDpid());
        staticFlowEntryPusher.addFlow(testRule2.getName(), testRule2.getFlowMod(), testRule2.getDpid());
        staticFlowEntryPusher.addFlow(testRule3.getName(), testRule3.getFlowMod(), testRule3.getDpid());
        EasyMock.verify(mockSwitch);

        // verify that flowpusher read all three entries from storage
        assertEquals(TotalTestRules, staticFlowEntryPusher.getFlows(TestSwitch1DPID).size());

        // Verify that the switch has gotten some flow_mods
        assertTrue(writeCaptureList.hasCaptured());
        assertEquals(TotalTestRules, writeCaptureList.getValues().size());

        // Order assumes how things are stored in hash bucket;
        // should be fixed because OFMessage.hashCode() is deterministic
        OFFlowMod firstFlowMod = (OFFlowMod) writeCaptureList.getValues().get(0).get(0);
        verifyFlowMod(firstFlowMod, testRule1.getFlowMod());
        OFFlowMod secondFlowMod = (OFFlowMod) writeCaptureList.getValues().get(1).get(0);
        verifyFlowMod(secondFlowMod, testRule2.getFlowMod());
        OFFlowMod thirdFlowMod = (OFFlowMod) writeCaptureList.getValues().get(2).get(0);
        verifyFlowMod(thirdFlowMod, testRule3.getFlowMod());

        writeCaptureList.reset();
        contextCapture.reset();
        EasyMock.reset(mockSwitch);

        EasyMock.expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        EasyMock.expect(mockSwitch.getStringId()).andReturn(TestSwitch1DPID).anyTimes();
        Capture<OFMessage> writeCapture = new Capture<OFMessage>(CaptureType.ALL);
        mockSwitch.write(capture(writeCapture), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.flush();
        expectLastCall().anyTimes();
        EasyMock.replay(mockSwitch);
        // delete two rules and verify they've been removed
        staticFlowEntryPusher.deleteFlow(testRule1.getName(), testRule1.getDpid());
        staticFlowEntryPusher.deleteFlow(testRule2.getName(), testRule1.getDpid());
        EasyMock.verify(mockSwitch);
        assertEquals(1, staticFlowEntryPusher.getFlows(TestSwitch1DPID).size());
        assertTrue(writeCapture.hasCaptured());
        assertEquals(2, writeCapture.getValues().size());

        //short savedCommand = testRule1.getFlowMod().getCommand();
        OFFlowMod firstDelete = (OFFlowMod) writeCapture.getValues().get(0);
        testRule1.getFlowMod().setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
        verifyFlowMod(firstDelete, testRule1.getFlowMod());
        //testRule1.getFlowMod().setCommand(savedCommand);

        //savedCommand = testRule2.getFlowMod().getCommand();
        OFFlowMod secondDelete = (OFFlowMod) writeCapture.getValues().get(1);

        testRule2.getFlowMod().setCommand(OFFlowMod.OFPFC_DELETE_STRICT);
        verifyFlowMod(secondDelete, testRule2.getFlowMod());
        //testRule2.getFlowMod().setCommand(savedCommand);

        // Test updating a flowmod
        writeCaptureList.reset();
        contextCapture.reset();
        EasyMock.reset(mockSwitch);
        EasyMock.expect(mockSwitch.getId()).andReturn(dpid).anyTimes();
        EasyMock.expect(mockSwitch.getStringId()).andReturn(TestSwitch1DPID).anyTimes();
        mockSwitch.write(capture(writeCaptureList), capture(contextCapture));
        expectLastCall().anyTimes();
        mockSwitch.flush();
        expectLastCall().anyTimes();
        EasyMock.replay(mockSwitch);
        //OFMatch savedMatch = testRule3.getFlowMod().getMatch();
        //testRule3.getFlowMod().setMatch(savedMatch.clone());
        testRule3.getFlowMod().getMatch().fromString("dl-dst=00:70:30:40:50:60");
        staticFlowEntryPusher.addFlow(testRule3.getName(), testRule3.getFlowMod(), testRule3.getDpid());
        //testRule3.getFlowMod().setMatch(savedMatch);
        System.out.println(staticFlowEntryPusher.getFlows(TestSwitch1DPID));
        assertEquals(1, staticFlowEntryPusher.getFlows(TestSwitch1DPID).size());
        assertTrue(writeCaptureList.hasCaptured());
        // make sure the message was written
        assertEquals(1, writeCaptureList.getValues().size());
        // make sure the delete and then the new flowmod were written
        assertEquals(2, writeCaptureList.getValues().get(0).size());
    }

    @Test
    public void testHARoleChanged() throws IOException {
        /* FIXME: what's the right behavior here ??
        // Send a notification that we've changed to slave
        mfp.dispatchRoleChanged(Role.SLAVE);
        // Make sure we've removed all our entries
        assert(staticFlowEntryPusher.entry2dpid.isEmpty());
        assert(staticFlowEntryPusher.entriesFromStorage.isEmpty());

        // Send a notification that we've changed to master
        mfp.dispatchRoleChanged(Role.MASTER);
        // Make sure we've learned the entries
        assert(staticFlowEntryPusher.entry2dpid.containsValue(TestSwitch1DPID));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod1));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod2));
        assert(staticFlowEntryPusher.entriesFromStorage.containsValue(FlowMod3));
        */
    }
}
