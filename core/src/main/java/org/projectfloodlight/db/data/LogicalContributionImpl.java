package org.projectfloodlight.db.data;

/**
 * Default implementation of the LogicalDataNode.Contribution interface.
 *
 * @author rob.vaterlaus@bigswitch.com
 */
class LogicalContributionImpl implements LogicalDataNode.Contribution {

    private DataSource dataSource;
    private DataNode dataNode;

    public LogicalContributionImpl(DataSource dataSource, DataNode dataNode) {
        this.dataSource = dataSource;
        this.dataNode = dataNode;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public DataNode getDataNode() {
        return dataNode;
    }
}
