package net.floodlightcontroller.topology;

import java.util.Date;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * This data structure is defined for a tunnel event.  The srcDPID and
 * dstDPID denote the two endpoints of a unidirectional tunnel link.
 * The time value indicates the time at which this event was created.
 * @author srini
 *
 */
public class TunnelEvent {

    public enum TunnelLinkStatus {
        UNKNOWN {
            @Override
            public String toString() {
                return "unknown";
            }
        },
        DOWN {
            @Override
            public String toString() {
                return "down";
            }
        },
        UP {
            @Override
            public String toString() {
                return "up";
            }
        },
        NOT_ENABLED {
            @Override
            public String toString() {
                return "not-enabled";
            }
        },
        NOT_ACTIVE {
            @Override
            public String toString() {
                return "not-active";
            }
        }
    }

    long srcDPID;
    long dstDPID;
    TunnelLinkStatus status;
    Date lastVerified;

    public TunnelEvent(long srcDPID, long dstDPID, TunnelLinkStatus status) {
        this.srcDPID = srcDPID;
        this.dstDPID = dstDPID;
        this.status = status;
        lastVerified = new Date();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dstDPID ^ (dstDPID >>> 32));
        result = prime * result + (int) (srcDPID ^ (srcDPID >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TunnelEvent other = (TunnelEvent) obj;
        if (dstDPID != other.dstDPID) return false;
        if (srcDPID != other.srcDPID) return false;
        return true;
    }

    public Date getLastVerified() {
        return lastVerified == null ? null : (Date) lastVerified.clone();
    }

    @SuppressFBWarnings(value="EI_EXPOSE_REP2")
    public void setLastVerified(Date lastVerified) {
        this.lastVerified = lastVerified;
    }

    public TunnelLinkStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "TunnelEvent [srcDPID=" + srcDPID + ", dstDPID=" + dstDPID
                + ", status=" + status + ", lastVerified=" + lastVerified + "]";
    }

    public void setStatus(TunnelLinkStatus status) {
        this.status = status;
    }

    public long getSrcDPID() {
        return srcDPID;
    }

    public long getDstDPID() {
        return dstDPID;
    }
}