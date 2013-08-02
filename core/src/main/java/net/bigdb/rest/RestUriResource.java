package net.bigdb.rest;

import java.util.ArrayList;
import java.util.List;

import net.bigdb.BigDBException;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.parser.XPathParserUtils;
import net.bigdb.schema.AbstractSchemaNodeVisitor;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ReferenceSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.TypeSchemaNode;
import net.bigdb.schema.TypedefSchemaNode;
import net.bigdb.service.Service;
import net.bigdb.service.Treespace;

import org.restlet.ext.jackson.JacksonRepresentation;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

public class RestUriResource extends BigDBResource {

    @Get("json")
    public Representation getJsonSchema() throws BigDBException {
        Service service = (Service) this.getContext().getAttributes().get(
                BigDBRestApplication.BIG_DB_SERVICE_ATTRIBUTE);
        String treespaceName =
                (String) this.getRequestAttributes().get("treespace");
        Treespace treespace = service.getTreespace(treespaceName);
        String pathString = this.getRequest().getResourceRef().getRemainingPart();
        if ((pathString == null) || pathString.isEmpty())
            pathString = "/";
        LocationPathExpression path =
                XPathParserUtils.parseSingleLocationPathExpression(pathString, null);
        SchemaNode schemaNode =
                treespace.getSchema().getSchemaNode(path);
        Visitor v = new Visitor();
        schemaNode.accept(v);
        List<String> uris = v.getUris();
        JacksonRepresentation<List<String>> representation =
                new JacksonRepresentation<List<String>>(uris);
        representation.setObjectMapper(mapper);
        return representation;
    }

    private static class Visitor extends AbstractSchemaNodeVisitor {
        private final List<String> uris = new ArrayList<String>();
        private final List<String> nodesOnPath = new ArrayList<String>();

        public List<String> getUris() {
            return this.uris;
        }

        private String getPath(String current) {
            StringBuilder sb = new StringBuilder();
            //sb.append("/");
            for (String name : nodesOnPath) {
                sb.append(name);
                sb.append("/");
            }
            sb.append(current);
            return sb.toString();
        }
        private void removeTail() {
            if (nodesOnPath.size() > 0) {
                nodesOnPath.remove(nodesOnPath.size() -1);
            }
        }
        private void addCurrentToPath(String current) {
            nodesOnPath.add(current);
        }
        @Override
        public Result visitEnter(ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
            String current = containerSchemaNode.getName();
            uris.add(getPath(current));
            this.addCurrentToPath(current);
            return Result.CONTINUE;
        }

        @Override
        public Result visitLeave(ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
            //String current = containerSchemaNode.getName();
            this.removeTail();
            return Result.CONTINUE;
        }

        @Override
        public Result visitEnter(ListSchemaNode listSchemaNode)
                throws BigDBException {
            String current = listSchemaNode.getName();
            uris.add(getPath(current));
            this.addCurrentToPath(current);
            return Result.CONTINUE;
        }

        @Override
        public Result visitLeave(ListSchemaNode listSchemaNode)
                throws BigDBException {
            this.removeTail();
            return Result.CONTINUE;
        }

        @Override
        public Result visitEnter(ListElementSchemaNode listElementSchemaNode)
                throws BigDBException {
            String keyFormat = null;
            for (String key : listElementSchemaNode.getKeyNodeNames()) {
                if (keyFormat != null) {
                    keyFormat += "|" + key;
                } else {
                    keyFormat = key;
                }
            }
            keyFormat = "{" + keyFormat + "}";
            uris.add(getPath(keyFormat));
            this.addCurrentToPath(keyFormat);
            return Result.CONTINUE;
        }

        @Override
        public Result visitLeave(ListElementSchemaNode listElementSchemaNode)
                throws BigDBException {
            this.removeTail();
            return Result.CONTINUE;
        }

        @Override
        public Result visit(LeafSchemaNode leafSchemaNode) throws BigDBException {
            String current = leafSchemaNode.getName();
            uris.add(getPath(current));
            return Result.CONTINUE;
        }

        @Override
        public Result visit(LeafListSchemaNode leafListSchemaNode)
                throws BigDBException
        {
            String current = leafListSchemaNode.getName();
            uris.add(getPath(current));
            return Result.CONTINUE;
        }

        @Override
        public Result visit(ReferenceSchemaNode referenceSchemaNode)
                throws BigDBException {
            String current = referenceSchemaNode.getName();
            uris.add(getPath(current));
            return Result.CONTINUE;
        }

        @Override
        public Result visit(TypedefSchemaNode typedefSchemaNode)
                throws BigDBException {
            return Result.CONTINUE;
        }
        @Override
        public Result visit(TypeSchemaNode typeSchemaNode)
                throws BigDBException {
            return Result.CONTINUE;
        }
    }
}
