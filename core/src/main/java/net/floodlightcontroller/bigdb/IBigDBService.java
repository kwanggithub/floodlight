package net.floodlightcontroller.bigdb;

import net.bigdb.BigDBException;
import net.bigdb.TreespaceNotFoundException;
import net.bigdb.data.ServerDataSource;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * This is the interface through which floodlight modules access BigDB.
 * They can access the BigDB Service interface and use it to read or write
 * data to BigDB or call attachDataSource to set up custom data sources.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
public interface IBigDBService extends IFloodlightService {
    /**
     * Access the BigDB service interface.
     * @return
     */
    public Service getService();

    /**
     * Access the main BigDB treespace for the controller.
     * @return
     * @throws TreespaceNotFoundException
     */
    public Treespace getControllerTreespace() throws TreespaceNotFoundException;

    /**
     * Access the data source for the controller for adding providers
     * of dynamic state to BigDB.
     * @return the BigDB request router for the controller data source
     */
    public ServerDataSource getControllerDataSource();

    /**
     * Begin running the BigDB service. This should only be called from the
     * core floodlight code.
     * @throws BigDBException
     */
    public void run() throws BigDBException;

    public void stop() throws BigDBException;
}
