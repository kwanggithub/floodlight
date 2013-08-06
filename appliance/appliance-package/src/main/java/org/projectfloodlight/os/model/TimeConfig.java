package org.projectfloodlight.os.model;

import org.projectfloodlight.os.IOSConfiglet.ConfigType;

import com.fasterxml.jackson.annotation.JsonProperty;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represent time zone and syncronization configuration
 * @author readams
 *
 */
@SuppressFBWarnings(value="EI_EXPOSE_REP")
public class TimeConfig implements OSModel {
    private String timeZone;
    private String[] ntpServers;

    @ConfigApplyType({ConfigType.TIME_ZONE})
    @JsonProperty("time-zone")
    public String getTimeZone() {
        return timeZone;
    }
    @JsonProperty("time-zone")
    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }
    @ConfigApplyType({ConfigType.TIME_NTP})
    @JsonProperty("ntp-servers")
    public String[] getNtpServers() {
        if (ntpServers == null) return new String[] {};
        return ntpServers;
    }
    @JsonProperty("ntp-servers")
    public void setNtpServers(String[] ntpServers) {
        this.ntpServers = ntpServers;
    }
}
