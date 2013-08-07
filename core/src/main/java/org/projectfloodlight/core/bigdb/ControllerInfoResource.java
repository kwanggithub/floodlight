package org.projectfloodlight.core.bigdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.core.IFloodlightProviderService.Role;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.FloodlightResource;
import org.projectfloodlight.db.data.annotation.BigDBPath;
import org.projectfloodlight.db.data.annotation.BigDBProperty;
import org.projectfloodlight.db.data.annotation.BigDBQuery;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ControllerInfoResource extends FloodlightResource {

    public static class HealthInfo {
        boolean _healthy;
        
        @BigDBProperty("healthy")
        public boolean getHealthy() {
            return _healthy;
        }

        public void setHealthy(boolean _healthy) {
            this._healthy = _healthy;
        }
    }

    @SuppressFBWarnings(value="EQ_COMPARETO_USE_OBJECT_EQUALS")
    // Note: this class has a natural ordering that is inconsistent with equals.
    public static class MemoryInfo implements Comparable<MemoryInfo> {
        Long _amount;
        String _memoryType;
        
        @BigDBProperty("amount")
        public Long getAmount() {
            return _amount;
        }

        public void setAmount(Long _amount) {
            this._amount = _amount;
        }

        @BigDBProperty("memory-type")
        public String getMemoryType() {
            return _memoryType;
        }

        public void setMemoryType(String _memoryType) {
            this._memoryType = _memoryType;
        }

        @Override
        public int compareTo(MemoryInfo o) {
            return _memoryType.compareTo(o._memoryType);
        }
    }

    public static class SummaryInfo {
        Long _info;
        String _infoShortDescription;
        
        @BigDBProperty("info")
        public Long getInfo() {
            return _info;
        }

        public void setInfo(Long _info) {
            this._info = _info;
        }

        @BigDBProperty("info-short-description")
        public String getInfoShortDescription() {
            return _infoShortDescription;
        }

        public void setInfoShortDescription(String _infoShortDescription) {
            this._infoShortDescription = _infoShortDescription;
        }
    }

    @BigDBQuery
    @BigDBPath("role")
    public String getRole() {
        Role role = getFloodlightProvider().getRole();
        return (role != null) ? role.toString().toLowerCase() : null;
    }

    @BigDBQuery
    @BigDBPath("up-time")
    public Long getUptime() {
        return getFloodlightProvider().getUptime();
    }

    @BigDBQuery
    @BigDBPath("memory")
    public List<MemoryInfo> getMemory() {
        List<MemoryInfo> model = new ArrayList<MemoryInfo>();
        Map<String, Long> m = getFloodlightProvider().getMemory();
        if (m == null) {
            return model;
        }
        for (Map.Entry<String, Long> e : m.entrySet()) {
            MemoryInfo mem = new MemoryInfo();
            mem.setMemoryType(e.getKey());
            mem.setAmount(e.getValue());
            model.add(mem);
        }
        Collections.sort(model);
        return model;
    }
    
    @BigDBQuery
    @BigDBPath("health")
    public HealthInfo getHealth() throws BigDBException {
        HealthInfo healthInfo = new HealthInfo();
        healthInfo.setHealthy(true);
        return healthInfo;
    }

    @BigDBQuery
    @BigDBPath("summary")
    public List<SummaryInfo> getSummary() {
        List<SummaryInfo>sum = new ArrayList<SummaryInfo>();
        Map<String, Object> info =
                getFloodlightProvider().getControllerInfo("summary");
        if (info != null) {
            for (Map.Entry<String, Object> e : info.entrySet()) {
                SummaryInfo s = new SummaryInfo();
                s.setInfoShortDescription(e.getKey());
                s.setInfo(Long.valueOf(((Integer)e.getValue()).intValue()));
                sum.add(s);
            }
        }
        return sum;
    }

}
