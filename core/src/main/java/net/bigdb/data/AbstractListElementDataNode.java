package net.bigdb.data;

public abstract class AbstractListElementDataNode extends AbstractDictionaryDataNode
        implements ListElementDataNode {

    @Override
    public NodeType getNodeType() {
        return NodeType.LIST_ELEMENT;
    }
}
