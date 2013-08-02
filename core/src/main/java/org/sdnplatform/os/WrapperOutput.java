package org.sdnplatform.os;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The output from {@link OSConfigWrapper}
 * @author readams
 */
public class WrapperOutput {
    public enum Status {
        SUCCESS,
        NONFATAL_ERROR,
        GENERIC_ERROR,
        IO_ERROR,
        SERIALIZATION_ERROR,
        SUBPROCESS_ERROR,
        ARG_ERROR,
    }
        
    public static class Item {
        private Status status;
        private String action;
        private String message;
        private String fullOutput;
        
        public Item() {
            super();
        }
        public Item(Status status, String action, 
                    String message, String fullOutput) {
            super();
            this.status = status;
            this.action = action;
            this.message = message;
            this.fullOutput = fullOutput;
        }

        public Status getStatus() {
            return status;
        }
        public void setStatus(Status status) {
            this.status = status;
        }
        public String getMessage() {
            return message;
        }
        public void setMessage(String message) {
            this.message = message;
        }
        public String getFullOutput() {
            return fullOutput;
        }
        public void setFullOutput(String fullOutput) {
            this.fullOutput = fullOutput;
        }
        public String getAction() {
            return action;
        }
        public void setAction(String action) {
            this.action = action;
        }
        @Override
        public String toString() {
            return "Item [status=" + status + ", action=" + action
                   + ", message=" + message + ", fullOutput=" + fullOutput
                   + "]";
        }
    }
    
    private Status overallStatus = Status.SUCCESS;
    private ArrayList<Item> items;
    
    // ***************
    // Getters/Setters
    // ***************

    public Status getOverallStatus() {
        return overallStatus;
    }
    public void setOverallStatus(Status overallStatus) {
        this.overallStatus = overallStatus;
    }
    public void addItem(Item error) {
        if (items == null)
            items = new ArrayList<Item>();
        items.add(error);
        if (!nonfatal() &&
            !overallStatus.equals(error.getStatus())) {
            overallStatus = Status.GENERIC_ERROR;
        } else if (Status.SUCCESS.equals(overallStatus)) {
            overallStatus = error.status;
        }
    }
    public List<Item> getItems() {
        if (items == null) return Collections.emptyList();
        return items;
    }
    public void add(WrapperOutput wo) {
        if (wo.items == null) return;
        for (Item i : wo.items) {
            addItem(i);
        }
    }
    public boolean succeeded() {
        return Status.SUCCESS.equals(overallStatus);
    }

    public boolean nonfatal() {
        return (Status.SUCCESS.equals(overallStatus) ||
                Status.NONFATAL_ERROR.equals(overallStatus));
    }
    
    @Override
    public String toString() {
        return "WrapperOutput [overallStatus=" + overallStatus + ", items="
               + items + "]";
    }
    // **************
    // Static methods
    // **************
    public static WrapperOutput success(String action) {
        return success(action, null);
    }
    public static WrapperOutput success(String action, String message) {
        WrapperOutput wo = new WrapperOutput();
        wo.addItem(new Item(Status.SUCCESS, action, message, null));
        return wo;
    }
    public static WrapperOutput error(Status status, String action,
                                      String msg, String fullOutput) {
        WrapperOutput wo = new WrapperOutput();
        wo.addItem(new Item(status, action, msg, fullOutput));
        return wo;
    }
    public static WrapperOutput error(Status status, String msg,
                                      String fullOutput) {
        return error(status, null, msg, fullOutput);
    }
    public static WrapperOutput error(Status status, String msg) {
        return WrapperOutput.error(status, null, msg, null);
    }
    public static WrapperOutput error(Status status, Exception e) {
        return error(status, null, e);
    }
    public static WrapperOutput error(Status status, String action,
                                      Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return WrapperOutput.error(status, action,
                                   e.getClass().getName() + ": " + e.getMessage(),
                                   sw.toString());
    }
}
