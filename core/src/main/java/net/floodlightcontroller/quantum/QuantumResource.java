package net.floodlightcontroller.quantum;

import java.io.InputStream;

import net.bigdb.BigDBException;
import net.bigdb.TreespaceNotFoundException;
import net.bigdb.auth.AuthContext;
import net.bigdb.query.Query;
import net.bigdb.rest.BigDBResource;
import net.bigdb.rest.BigDBRestApplication;
import net.bigdb.rest.Patch;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;
import net.floodlightcontroller.bigdb.EmbeddedBigDBService;

import org.restlet.resource.Delete;
import org.restlet.resource.Post;
import org.restlet.resource.Put;

public abstract class QuantumResource extends BigDBResource {
    private Treespace getControllerTreespace() throws TreespaceNotFoundException {
        Service service =
                (Service) getContext().getAttributes().get(BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
        return service.getTreespace(EmbeddedBigDBService.CONTROLLER_TREESPACE_NAME);
    }

    /**
     * Defines the query to be executed against BigDB.
     * @return The BigDB query to be used.
     * @throws BigDBException If there was a problem constructing the query.
     */
    protected abstract Query getBigDBQuery() throws BigDBException;

    /**
     * Creates the InputStream of the serialized json data.
     * @param postData The serialized JSON data as a string.
     * @return An InputStream containing the JSON.
     * @throws Exception If there was a problem creating the Input Stream.
     */
    protected abstract InputStream getJsonInputData(String postData) throws Exception;

    @Post
    @Patch
    public void modifyQuantumState(String postData) throws Exception {
        getControllerTreespace().updateData(getBigDBQuery(), Treespace.DataFormat.JSON,
                getJsonInputData(postData), AuthContext.SYSTEM);
    }

    @Put
    public void createQuantumState(String postData) throws Exception {
        getControllerTreespace().replaceData(getBigDBQuery(), Treespace.DataFormat.JSON,
                getJsonInputData(postData), AuthContext.SYSTEM);
    }

    @Delete
    public void deleteQuantumState() throws Exception {
        getControllerTreespace().deleteData(getBigDBQuery(), AuthContext.SYSTEM);
    }
}
