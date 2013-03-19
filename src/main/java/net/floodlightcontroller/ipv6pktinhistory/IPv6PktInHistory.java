package net.floodlightcontroller.ipv6pktinhistory;

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.SwitchMessagePair;
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv6;

public class IPv6PktInHistory implements IFloodlightModule, IOFMessageListener {
	protected static Logger log = LoggerFactory.getLogger(IPv6PktInHistory.class);
	protected IFloodlightProviderService floodlightProvider;
	protected ConcurrentCircularBuffer<SwitchMessagePair> buffer;
	
	@Override
	public String getName() {
		return "IPv6PktInHistory";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch(msg.getType()) {
        case PACKET_IN:
            Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, 
                    IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

            if (eth.getEtherType() == Ethernet.TYPE_IPv6) {
                buffer.add(new SwitchMessagePair(sw, msg));

            	IPv6 pkt = (IPv6) eth.getPayload();

            	String srcIPv6 = "null";
            	try {
            		srcIPv6 = Inet6Address.getByAddress(pkt.getSourceAddress()).toString();
            	} catch (UnknownHostException e) {
            		e.printStackTrace();
            	}
            	String dstIPv6 = "null";
            	try {
            		dstIPv6 = Inet6Address.getByAddress(pkt.getDestinationAddress()).toString();
            	} catch (UnknownHostException e) {
            		e.printStackTrace();
            	}
            	
            	log.debug("IPv6 flow: SOURCE: {} --> DESTINATION: {}", srcIPv6, dstIPv6);
            }
            break;
       default:
           break;
    }
    return Command.CONTINUE;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		buffer = new ConcurrentCircularBuffer<SwitchMessagePair>(SwitchMessagePair.class, 100);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
	}

}
