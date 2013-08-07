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

package org.projectfloodlight.learningswitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openflow.util.HexString;
import org.projectfloodlight.core.IFloodlightProviderService;
import org.projectfloodlight.core.IOFSwitch;
import org.projectfloodlight.core.types.MacVlanPair;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LearningSwitchTable extends ServerResource {
    protected static final Logger log = 
            LoggerFactory.getLogger(LearningSwitchTable.class);

    protected Map<String, Object> formatTableEntry(MacVlanPair key, short port) {
        Map<String, Object> entry = new HashMap<String, Object>();
        entry.put("mac", HexString.toHexString(key.mac));
        entry.put("vlan", key.vlan);
        entry.put("port", port);
        return entry;
    }

    protected List<Map<String, Object>> getOneSwitchTable(Map<MacVlanPair, Short> switchMap) {
        List<Map<String, Object>> switchTable = new ArrayList<Map<String, Object>>();
        for (Entry<MacVlanPair, Short> entry : switchMap.entrySet()) {
            switchTable.add(formatTableEntry(entry.getKey(), entry.getValue()));
        }
        return switchTable;
    }

    @Get("json")
    public Map<String, List<Map<String, Object>>> getSwitchTableJson() {
        ILearningSwitchService lsp =
                (ILearningSwitchService)getContext().getAttributes().
                    get(ILearningSwitchService.class.getCanonicalName());

        Map<IOFSwitch, Map<MacVlanPair,Short>> table = lsp.getTable();
        Map<String, List<Map<String, Object>>> allSwitchTableJson = new HashMap<String, List<Map<String, Object>>>();

        String switchId = (String) getRequestAttributes().get("switch");
        if (switchId.toLowerCase().equals("all")) {
            for (Entry<IOFSwitch,Map<MacVlanPair,Short>> entry : table.entrySet()) {
                IOFSwitch sw= entry.getKey();
                allSwitchTableJson.put(HexString.toHexString(sw.getId()), 
                                       getOneSwitchTable(entry.getValue()));
            }
        } else {
            try {
                IFloodlightProviderService floodlightProvider =
                        (IFloodlightProviderService)getContext().getAttributes().
                            get(IFloodlightProviderService.class.getCanonicalName());
                long dpid = HexString.toLong(switchId);
                IOFSwitch sw = floodlightProvider.getSwitch(dpid);
                allSwitchTableJson.put(HexString.toHexString(sw.getId()), getOneSwitchTable(table.get(sw)));
            } catch (NumberFormatException e) {
                log.error("Could not decode switch ID = " + switchId);
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
            }
        }

        return allSwitchTableJson;
    }
}
