package org.projectfloodlight.db.rest;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.query.parser.XPathParserUtils;
import org.projectfloodlight.db.schema.SchemaNode;
import org.projectfloodlight.db.service.Service;
import org.projectfloodlight.db.service.Treespace;
import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaResource extends BigDBResource {
    protected final static Logger logger =
            LoggerFactory.getLogger(SchemaResource.class);

    @Get("json")
    public Representation getJsonSchema() throws BigDBException {
        String treespaceName = "";
        String pathString = "";
        try {
            Service service = (Service) this.getContext().getAttributes().get(
                                   BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
            treespaceName =
                    (String) this.getRequestAttributes().get("treespace");
            Treespace treespace = service.getTreespace(treespaceName);
            pathString = this.getRequest().getResourceRef().getRemainingPart();
            if ((pathString == null) || pathString.isEmpty())
                pathString = "/";
            LocationPathExpression path =
                    XPathParserUtils.parseSingleLocationPathExpression(
                            pathString, null);
            SchemaNode schemaNode =
                    treespace.getSchema().getSchemaNode(path);
            JacksonRepresentation<SchemaNode> representation =
                    new JacksonRepresentation<SchemaNode>(schemaNode);
            representation.setObjectMapper(mapper);
            return representation;
        } catch (Exception e) {
            String message = "Failed to query schema for " + treespaceName +
                             " with query " + pathString;
            logger.error(message + (e.getMessage() == null ? "" :
                                    " with error " + e.getMessage()));
            throw new BigDBException(message, e);
        }
    }
}
