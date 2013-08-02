package net.bigdb.tools.docgen;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import net.bigdb.BigDBException;
import net.bigdb.schema.AggregateSchemaNode;
import net.bigdb.schema.ContainerSchemaNode;
import net.bigdb.schema.EnumTypeSchemaNode;
import net.bigdb.schema.GroupingSchemaNode;
import net.bigdb.schema.LeafListSchemaNode;
import net.bigdb.schema.LeafSchemaNode;
import net.bigdb.schema.ListElementSchemaNode;
import net.bigdb.schema.ListSchemaNode;
import net.bigdb.schema.ReferenceSchemaNode;
import net.bigdb.schema.ScalarSchemaNode;
import net.bigdb.schema.SchemaNode;
import net.bigdb.schema.SchemaNodeVisitor;
import net.bigdb.schema.TypeSchemaNode;
import net.bigdb.schema.TypedefSchemaNode;
import net.bigdb.schema.UsesSchemaNode;
import net.bigdb.yang.EnumStatement;

/**
 * A Schema tree visitor to emit document.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public class DocGenerator implements SchemaNodeVisitor {

    static final String doubleNewLine = "(\r\r|\r\n\r\n|\n\n)";
    static final String singleNewLine = "(\r|\n|\r\n)";

    IDocFormatter formatter;

    Stack<SchemaNode> stack = new Stack<SchemaNode>();

    private Map<String, List<SampleQuery>> samples = new HashMap<String, List<SampleQuery>>();

    public DocGenerator(String sampleFile, String path, IDocFormatter formatter)
            throws BigDBException {
        this.formatter = formatter;
        if (sampleFile != null)
            samples = SampleQueryManager.readSampleQueries(sampleFile);
    }

    private void createWriter(SchemaNode node) throws BigDBException {
        this.formatter.createWriter(node.getName() + ".txt");
    }

    private void closeWriter() throws BigDBException {
        this.formatter.closeWriter();
    }

    protected String getNodeQualifiedName(SchemaNode ni)
            throws BigDBException {
        return this.getNodeQualifiedName(ni, false, false);
    }
    protected String getNodeQualifiedName(SchemaNode ni, boolean selfWithPredicate,
                                          boolean withPredicate)
            throws BigDBException {
        String name = null;
        if (ni == null) {
            return "";
        }
        name = ni.getName();
        if (name == null || name.isEmpty() || name.equals("root")) {
            return "";
        }
        StringBuilder predicates = new StringBuilder();
        if (selfWithPredicate && ni.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
            ListElementSchemaNode ln = (ListElementSchemaNode)ni;
            List<String> keys = ln.getKeyNodeNames();
            for (String kn : keys) {
                // TODO: add a sample value
                SchemaNode n = ln.getChildSchemaNode(kn);
                String sampleValue = n.getAttributeStringValue("sample-value");
                if (sampleValue == null) {
                    sampleValue = "VALUE";
                }
                predicates.append("\\[").append(kn).append ("=\"").append(sampleValue).append("\"\\]");
            }
        }
        SchemaNode parent = ni.getParentSchemaNode();
        if (parent.getNodeType() == SchemaNode.NodeType.LIST) {
            // Skip the list node since the list name is the same node as list element
            parent = parent.getParentSchemaNode();
        }
        return this.getNodeQualifiedName(parent, withPredicate, withPredicate) +
                "/" + name + predicates.toString();

    }

    public String getFileName(SchemaNode nodeInfo, String path) {
        return path + File.separator + nodeInfo.getName() + ".txt";
    }


    private Result enter(AggregateSchemaNode containerNode)
            throws BigDBException {
        if (containerNode.getName().isEmpty()) {
            containerNode.setName("root");
            this.createWriter(containerNode);
            emitFileHeader("");
        }
        stack.push(containerNode);
        emitContainerStart(containerNode);
        return Result.CONTINUE;
    }

    protected Result leave(AggregateSchemaNode containerNode)
            throws BigDBException {
        SchemaNode node = stack.pop();

        if (node.getName().isEmpty()) {
            closeWriter();
        }
        this.emitContainerEnd(node);

        return Result.CONTINUE;
    }

    public void emitContainerStart(AggregateSchemaNode node)
            throws BigDBException {

        this.emitUrl(node);
        this.emitNode(node);
        AggregateSchemaNode an = node;
        // emit the children
        if (an.getChildNodes().size() > 0) {
            this.formatter.emitHeader(2, 1, "child nodes:");
            this.formatter.emitTableHeader(new String[] {"Name", "Node Type",
                                    "Data Type", "Description"});
        }
        for (Map.Entry<String, SchemaNode> e : an.getChildNodes().entrySet()) {
            this.emitChildNode(e.getValue());
        }
        formatter.emitSectionEnd();
    }

    // TODO: improve performance
    private String getDescriptionSummary(String description) {
        String[] parags = description.split(singleNewLine);
        return parags[0];
    }

    private void emitContainerEnd(SchemaNode node)
            throws BigDBException {
    }

    // TODO: The contents should be put in files and loaded here.
    private void emitFileHeader(String description) throws BigDBException {
        formatter.emitHeader(0, 0, "General Guidelines");
        formatter.emitHeader(1, 0, "Schemas");
        formatter.emitText(0, "Description of yang schema");
        formatter.emitHeader(1, 0, "Containers, Lists and Leaves");
        formatter.emitHeader(1, 0, "XPath Query");
        formatter.emitText(0, "BigDB rest API supports a subset of XPath to query data. " +
                         "More ...");
        formatter.emitHeader(0, 0, "REST API");
    }

    public void emitUrl(SchemaNode ni) throws BigDBException {
        formatter.emitHeader(1, 0, getNodeQualifiedName(ni));
    }

    public void emitName(SchemaNode ni) throws BigDBException {
        formatter.emitHeader(2, 1, "name");
        formatter.emitText(2, ni.getName());
    }

    public void emitSampleQuery(SchemaNode ni, SampleQuery sq)
            throws BigDBException {
        String queryUri = sq.getQueryUri();
        queryUri = formatter.getSkippedString(queryUri);

        String response = null;
        String input = null;

        if (sq.getQueryResponseResourceName() != null && !sq.getQueryResponseResourceName().isEmpty()) {
            response = SampleQueryManager.readResponse(sq.getQueryResponseResourceName());
        }
        if (sq.getQueryInputResourceName() != null && !sq.getQueryInputResourceName().isEmpty()) {
            input = SampleQueryManager.readResponse(sq.getQueryInputResourceName());
        }

        if (response != null || input != null) {
            formatter.emitText(2, sq.getOperation() + " " + queryUri, false);
        }
        if (response != null && !response.isEmpty()) {
            formatter.emitCodeTextWithExpand("response", response);
        }
        if (input != null && !input.isEmpty()) {
            formatter.emitCodeTextWithExpand("input", input);
        }
    }
    public void emitSampleQuery(SchemaNode ni)
            throws BigDBException {
        String l = this.getNodeQualifiedName(ni, false, false);
        List<SampleQuery> al = samples.get(l);

        formatter.emitHeader(2, 1, "sample query");
        if (al != null && al.size() > 0) {
            for (SampleQuery sq : al) {
                // emit all sample queries
                emitSampleQuery(ni, sq);
            }
        }
    }
    public void emitDescription(SchemaNode ni) throws BigDBException {
        formatter.emitHeader(2, 1, "description");
        String description = ni.getDescription();
        if (description == null &&
            ni.getNodeType() == SchemaNode.NodeType.LIST_ELEMENT) {
            description = ni.getParentSchemaNode().getDescription();
        }
        if (description == null || description.isEmpty()) {
            description = "*Description is missing, please add description.*";
        }
        int i = description.indexOf('\n');
        String summary = i > 0 ? description.substring(0, i) : "";
        String body = description.substring(i + 1);

        if (!summary.isEmpty()) {
            formatter.emitText(2, formatter.getSkippedString(summary));
            formatter.emitText(2, " ");
        }

        formatter.emitText(2, formatter.getSkippedString(body));
        emitSampleQuery(ni);
    }

    public void emitSupportedOperations(SchemaNode ni) throws BigDBException {
        String supportedOperations = "";
        supportedOperations = "GET";
        if (ni.getActualConfig()) {
            supportedOperations += ", PUT, POST";
        }
        formatter.emitHeader(2, 1, "supported operations");
        formatter.emitText(2, supportedOperations);
    }

    public void emitChildNode(SchemaNode ni) throws BigDBException {
        SchemaNode node = ni;
        LeafSchemaNode leafNode = null;
        if (node.getNodeType() == SchemaNode.NodeType.LEAF) {
            leafNode = (LeafSchemaNode)node;
        } else if (node.getNodeType() == SchemaNode.NodeType.LEAF_LIST) {
            leafNode = ((LeafListSchemaNode)node).getLeafSchemaNode();
        }
        String itemName = null;
        if (leafNode == null) {
            itemName = formatter.getStringWithLink(ni.getName(),
                                                   getNodeQualifiedName(ni));
        } else {
            itemName = ni.getName();
        }
        String nodeType = ni.getNodeType().toString();
        String leafType = "";
        if (leafNode != null) {
            leafType = leafNode.getLeafType().toString();
        }
        String description = ni.getDescription();
        if (description == null) {
            description = "";
        } else if (ni.getNodeType() != SchemaNode.NodeType.LEAF &&
                   ni.getNodeType() != SchemaNode.NodeType.LEAF_LIST){
            description = this.getDescriptionSummary(description);
        }
        // handle enumeration type
        ScalarSchemaNode sn = null;
        if (ni.getNodeType() == SchemaNode.NodeType.LEAF) {
            sn = (ScalarSchemaNode)ni;
        } else if (ni.getNodeType() == SchemaNode.NodeType.LEAF_LIST) {
            sn = ((LeafListSchemaNode)ni).getLeafSchemaNode();
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        if (sn != null &&
                sn.getBaseTypedef().getLeafType() == SchemaNode.LeafType.ENUMERATION) {
            EnumTypeSchemaNode en = (EnumTypeSchemaNode)sn.getBaseTypedef();
            for (Map.Entry<String, EnumStatement> e :
                en.getEnumerationSpecifications().entrySet()) {
                if (pos > 0) {
                    sb.append(", ");
                    if (pos == en.getEnumerationSpecifications().size() - 1) {
                        sb.append("or ");
                    }
                }
                pos++;
                sb.append(e.getValue().getName());
            }
        }
        if (sb.length() > 0) {
            if (!description.isEmpty()) {
                description += " ";
            }
            description = description + "Possible values are " + sb.toString();
        }
        description = formatter.getSkippedString(description);
        formatter.emitTableRow(new String[] {itemName, nodeType, leafType, description});
    }

    public void emitNode(SchemaNode ni)
            throws BigDBException {
        this.emitName(ni);
        this.emitDescription(ni);
        this.emitSupportedOperations(ni);
    }


    @Override
    public Result
        visitEnter(ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
        return this.enter(containerSchemaNode);
    }

    @Override
    public Result
        visitLeave(ContainerSchemaNode containerSchemaNode)
                throws BigDBException {
            return leave(containerSchemaNode);
    }

    @Override
    public Result
        visitEnter(ListSchemaNode listSchemaNode) throws BigDBException {
        listSchemaNode.getListElementSchemaNode().setName(listSchemaNode.getName());
        return this.enter(listSchemaNode.getListElementSchemaNode());
    }

    @Override
    public Result
            visitLeave(ListSchemaNode listSchemaNode) throws BigDBException {
        this.leave(listSchemaNode.getListElementSchemaNode());
        return null;
    }
    @Override
    public Result
            visitEnter(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result
            visitLeave(ListElementSchemaNode listElementSchemaNode)
            throws BigDBException {

        return null;
    }

    @Override
    public Result visit(LeafSchemaNode leafSchemaNode) throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result
        visit(LeafListSchemaNode leafListSchemaNode)
                throws BigDBException {
        return Result.CONTINUE;
    }

    @Override
    public Result
        visit(ReferenceSchemaNode referenceSchemaNode)
            throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result
            visit(TypedefSchemaNode typedefSchemaNode) throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result visit(UsesSchemaNode usesSchemaNode) throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result visit(TypeSchemaNode typeSchemaNode) throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result
            visitEnter(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Result
            visitLeave(GroupingSchemaNode groupingSchemaNode)
            throws BigDBException {
        // TODO Auto-generated method stub
        return null;
    }
}
