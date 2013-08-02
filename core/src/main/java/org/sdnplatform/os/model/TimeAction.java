package org.sdnplatform.os.model;

import java.util.Date;

import org.sdnplatform.os.IOSActionlet.ActionType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Action model representing resetting the system time.
 * @author readams
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class TimeAction implements OSModel {
    private String ntpServer;
    private Date systemTime;

    @ActionApplyType({ActionType.SET_TIME})
    public Date getSystemTime() {
        return systemTime;
    }
    public void setSystemTime(Date systemTime) {
        this.systemTime = systemTime;
    }
    @ActionApplyType({ActionType.NTP_DATE})
    public String getNtpServer() {
        return ntpServer;
    }
    public void setNtpServer(String ntpServer) {
        this.ntpServer = ntpServer;
    }
}
