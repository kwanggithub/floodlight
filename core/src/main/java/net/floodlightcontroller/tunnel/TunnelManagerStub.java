package net.floodlightcontroller.tunnel;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.device.IDevice;


/**
 * Stub version of tunnel manager.  Rewrite needed to bring this functionality
 * into floodlight
 */

public class TunnelManagerStub
    implements ITunnelService, IFloodlightModule
{
    // **************
    // ITunnelService
    // **************

    @Override
    public SwitchTunnelInfo getTunnelsOnSwitch(long swDpid) {
        return new SwitchTunnelInfo(swDpid, null, null, false, false, false);
    }

    @Override
    public Collection<SwitchTunnelInfo> getAllTunnels() {
        return Collections.emptySet();
    }

    @Override
    public boolean isTunnelEndpoint(IDevice dev) {
        return false;
    }

    @Override
    public boolean isTunnelSubnet(int ipAddress) {
        return false;
    }

    @Override
    public Integer getTunnelIPAddr(long dpid) {
        return null;
    }

    @Override
    public boolean isTunnelActiveByIP(int ipAddr) {
        return false;
    }

    @Override
    public boolean isTunnelActiveByDpid(long dpid) {
        return false;
    }

    @Override
    public Short getTunnelPortNumber(long dpid) {
        return null;
    }

    @Override
    public void updateTunnelIP(long dpid1, long dpid2) {
        
    }

    @Override
    public Long getSwitchDpid(int tunnelIPAddress) {
        return null;
    }

    @Override
    public boolean isTunnelLoopbackPort(long switchDPID, short portnum) {
        return false;
    }

    @Override
    public Short getTunnelLoopbackPort(long switchDPID) {
        return null;
    }

    @Override
    public void addListener(ITunnelManagerListener listener) {
        
    }

    @Override
    public void removeListener(ITunnelManagerListener listener) {
        
    }
    
    // *****************
    // IFloodlightModule
    // *****************

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleServices() {
        return Collections.<Class<? extends IFloodlightService>>
            singleton(ITunnelService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return ImmutableMap.<Class<? extends IFloodlightService>, 
                             IFloodlightService>of(ITunnelService.class, this);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>>
            getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {

    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {

    }
}
