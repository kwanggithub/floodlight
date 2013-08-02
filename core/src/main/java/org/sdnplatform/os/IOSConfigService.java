package org.sdnplatform.os;

import org.sdnplatform.os.model.OSAction;
import org.sdnplatform.os.model.OSConfig;

import net.bigdb.data.DataNode;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Service that allows modifying the configuration of the underlying operating
 * system.  The config service will pass changed configuration onto one or more
 * {@link IOSConfiglet} objects registered with the service.  The configlets
 * will run in a separate privileged process.
 * @author readams
 */
public interface IOSConfigService extends IFloodlightService {
    /**
     * Apply the given OS configuration to the system
     * @param config the raw {@link OSConfig} object
     * @return the output resulting from applying the configuration
     */
    public WrapperOutput applyConfiguration(OSConfig config);

    /**
     * Apply the given OS configuration to the system
     * @param config A {@link DataNode} referencing the root of the
     * /os-config path.
     * @return The output resulting from applying the configuration
     */
    public WrapperOutput applyConfiguration(DataNode config);

    /**
     * Apply the given OS action to the system
     * @param action A {@link OSAction} describing the actions to take
     * on the system
     * @return The output resulting from applying the action
     */
    public WrapperOutput applyAction(OSAction action);
}
