package org.projectfloodlight.db.tools.docgen;

/**
 * Helper class to wrap information for a sample query.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */

public class SampleQuery {

    private String queryContainerUri = "";
    private String queryUri = "";
    private String operation = "GET";
    private String queryResponseResourceName = "";
    private String queryInputResourceName = "";

    public SampleQuery() {
        
    }

    public SampleQuery(String containerUri, String queryUri, String operation,
                       String input, String response) {
        this.queryContainerUri = containerUri;
        this.queryUri = queryUri;
        this.operation = operation;
        this.queryInputResourceName = input;
        this.queryResponseResourceName = response;
    }

    public String getQueryContainerUri() {
        return queryContainerUri;
    }
    public void setQueryContainerUri(String queryContainerUri) {
        this.queryContainerUri = queryContainerUri;
    }
    public String getQueryUri() {
        return queryUri;
    }
    public void setQueryUri(String queryUri) {
        this.queryUri = queryUri;
    }
    public String getQueryResponseResourceName() {
        return queryResponseResourceName;
    }
    public void setQueryResponseResourceName(String queryResponseResourceName) {
        this.queryResponseResourceName = queryResponseResourceName;
    }
    public String getQueryInputResourceName() {
        return queryInputResourceName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setQueryInputResourceName(String queryInputResourceName) {
        this.queryInputResourceName = queryInputResourceName;
    }
    
}
