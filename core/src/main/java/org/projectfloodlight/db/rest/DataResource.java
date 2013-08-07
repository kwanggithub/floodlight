package org.projectfloodlight.db.rest;

import java.io.InputStream;
import java.util.concurrent.ConcurrentMap;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.BigDBInternalError;
import org.projectfloodlight.db.auth.AuthContext;
import org.projectfloodlight.db.auth.AuthService;
import org.projectfloodlight.db.auth.AuthenticationException;
import org.projectfloodlight.db.auth.AuthorizationException;
import org.projectfloodlight.db.data.DataNode;
import org.projectfloodlight.db.data.DataNodeSet;
import org.projectfloodlight.db.data.DataNodeUtilities;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.rest.auth.AuthContextFactory;
import org.projectfloodlight.db.schema.Schema;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.service.BigDBOperation;
import org.projectfloodlight.db.service.Service;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.data.Form;
import org.restlet.data.Reference;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataResource extends BigDBResource {
    protected final static Logger logger =
            LoggerFactory.getLogger(DataResource.class);
    private boolean authEnabled;
    private AuthContextFactory authContextFactory;

    @Override
    protected void doInit() throws ResourceException {
        super.doInit();

        ConcurrentMap<String, Object> attributes = getContext().getAttributes();

        AuthService authService = (AuthService) attributes.get(BigDBRestApplication.BIGDB_AUTH_SERVICE);

        if (authService != null) {
            authEnabled = true;
            authContextFactory = new AuthContextFactory(authService.getSessionManager(),
                                                        authService.getApplicationAuthenticator(),
                                                        authService.getConfig());
        }
    }

    private static boolean parseBooleanValue(String value) throws BigDBException {
        String lowerValue = value.toLowerCase();
        if (lowerValue.equals("true"))
            return true;
        if (lowerValue.equals("false"))
            return false;
        throw new BigDBException("Invalid boolean query parameter: " + value);
    }

    @Get("json")
    public Representation queryDataJson()
            throws BigDBException, AuthenticationException {
        String basePathString = "";
        try {
            // Get the remaining part of the URL, decoded and not including
            // the query parameters.
            Reference resourceRef = getRequest().getResourceRef();
            basePathString = resourceRef.getRemainingPart(true, false);

            Service service = (Service) this.getContext().getAttributes().get(
                              BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
            AuthContext authContext = getAuthContext();

            String treespaceName =
                    (String) this.getRequestAttributes().get("treespace");
            Treespace treespace = service.getTreespace(treespaceName);

            Query.Builder builder = Query.builder();
            if ((basePathString == null) || basePathString.isEmpty())
                basePathString = "/";
            builder.setBasePath(basePathString);
            Form form = getQuery();
            String[] selectedPathArray = form.getValuesArray("select");
            if (selectedPathArray != null) {
                for (String selectedPath: selectedPathArray) {
                    builder.addSelectPath(selectedPath);
                }
            }
            String configValue = form.getFirstValue("config", true);
            if (configValue != null) {
                boolean config = parseBooleanValue(configValue);
                builder.setIncludedStateType(config ? Query.StateType.CONFIG :
                    Query.StateType.OPERATIONAL);
            }
            String singleValue = form.getFirstValue("single", true);
            boolean single = (singleValue != null) ?
                    parseBooleanValue(singleValue) : false;
            Query query = builder.getQuery();
            DataNodeSet dataNodeSet = treespace.queryData(query, authContext);
            if (single) {
                Schema schema = treespace.getSchema();
                SchemaNode rootSchemaNode = schema.getSchemaNode(
                        LocationPathExpression.ROOT_PATH);
                boolean matchesMultipleDataNodes =
                        DataNodeUtilities.pathMatchesMultipleDataNodes(
                                query.getBasePath(), rootSchemaNode);
                if (matchesMultipleDataNodes) {
                    throw new BigDBException(
                            "The \"single\" query parameter can only be used " +
                            "with resource paths that map to a single result");
                }
                DataNode singleDataNode = dataNodeSet.getSingleDataNode();
                if (singleDataNode.isNull()) {
                    throw new BigDBException("Requested data does not exist",
                            BigDBException.Type.NOT_FOUND);
                }
                JacksonRepresentation<DataNode> representation =
                        new JacksonRepresentation<DataNode>(singleDataNode);
                representation.setObjectMapper(mapper);
                return representation;
            } else {
                JacksonRepresentation<DataNodeSet> representation =
                        new JacksonRepresentation<DataNodeSet>(dataNodeSet);
                representation.setObjectMapper(mapper);
                return representation;
            }
        } catch (BigDBException e) {
            String message = "Failed to query data for " + basePathString +
                            (e.getMessage() == null ? "" : " with error " + e.getMessage());
            // We test for NOT_FOUND errors here to handle the non-erroneous case
            // where the user queries for data that doesn't exist. Currently
            // this only happens with the single=true query parameter and the
            // exception that's thrown further up in this function. In that
            // case we don't want to log at error level since it's not really
            // an error.
            // FIXME: It's possible that there are other error types that should
            // also be logged as info instead of error. We should do an audit
            // pass over the different exceptions that can be thrown and decide
            // if they should be error vs. info. May possibly need to add a
            // field to BigDBException for that. For now though since we're
            // close to the end of this release we'll do the more conservative
            // fix and only check for the case that we know for sure we want to
            // map to info.
            if (e.getErrorType() == BigDBException.Type.NOT_FOUND)
                    logger.info(message);
            else
                    logger.error(message, e);
            // handled by StatusService
            throw e;
        } catch (Exception e) {
            String message = "Failed to query data for " + basePathString;
            logger.error(message + (e.getMessage() == null ? "" :
                                     " with error " + e.getMessage()), e);
            throw new BigDBException(message, e);
        }
    }


    private AuthContext getAuthContext() throws AuthorizationException {
        if (!authEnabled)
            return AuthContext.SYSTEM;
        else
            return authContextFactory.getAuthContext(getRequest());
    }

    private void mutateData(BigDBOperation operation, Representation entity,
            Variant variant) throws BigDBException {
        String queryString = "";
        try {
            queryString = this.getRequest().getResourceRef().getRemainingPart();
            if ((queryString == null) || queryString.isEmpty())
                queryString = "";
            Query query = Query.parse(queryString);
            Service service = (Service) this.getContext().getAttributes().get(
                    BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
            AuthContext authContext = getAuthContext();
            String treespaceName =
                    (String) this.getRequestAttributes().get("treespace");
            Treespace treespace = service.getTreespace(treespaceName);
            InputStream inputStream = null;
            if (operation != BigDBOperation.DELETE)
                inputStream = entity.getStream();

            switch (operation) {
            case INSERT:
                treespace.insertData(query, Treespace.DataFormat.JSON, inputStream,
                    authContext);
                break;
            case REPLACE:
                treespace.replaceData(query, Treespace.DataFormat.JSON, inputStream,
                        authContext);
                break;
            case UPDATE:
                treespace.updateData(query, Treespace.DataFormat.JSON, inputStream,
                        authContext);
                break;
            case DELETE:
                treespace.deleteData(query, authContext);
                break;
            default:
                throw new BigDBInternalError(
                        "Unsupported mutation operation: " + operation);
            }
        }
        catch (AuthorizationException e) {
            logger.warn("Permission denied: operation: {}; query: {}; {}", new Object[] { operation, queryString, e.getMessage() });
            if(logger.isDebugEnabled()) {
                logger.debug("Exception stack trace: ", e);
            }
            throw e;
        }
        catch (Exception e) {
            String exceptionMessage = e.getMessage();
            if (exceptionMessage == null)
                exceptionMessage = "";
            String message = String.format(
                    "Mutation operation failed; operation: %s; query: %s; %s",
                    operation, queryString, exceptionMessage);
            logger.error(message, e);
            if (e instanceof BigDBException)
                throw (BigDBException) e;
            throw new BigDBException(message, e);
        }
    }

    @Post
    public void insertDataJson(Representation entity, Variant variant)
            throws Exception {
        mutateData(BigDBOperation.INSERT, entity, variant);
    }

    @Put
    public void replaceDataJson(Representation entity, Variant variant)
            throws Exception {
        mutateData(BigDBOperation.REPLACE, entity, variant);
    }

    @Patch
    public void updateDataJson(Representation entity, Variant variant)
            throws Exception {
        mutateData(BigDBOperation.UPDATE, entity, variant);
    }

    @Delete
    public void deleteData(Representation entity, Variant variant)
            throws Exception {
        mutateData(BigDBOperation.DELETE, entity, variant);
    }
}
