package org.projectfloodlight.db.auth;

import java.util.Collection;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.google.common.collect.ImmutableSet;

/**
 * Immutable bean representing a user logged into a session, and his or her
 * permission metadata.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class BigDBUser {
    public final static String PREDEFINED_ADMIN_NAME = "admin";

    /** canonical user name (principal) */
    private final String user;

    /** Descriptive full user name, e.g., for UI purposes */
    private final String fullName;

    /** lists of groups this user belongs to */
    private final Set<BigDBGroup> groups;

    @JsonCreator
    public BigDBUser(@JsonProperty("user") String user,
            @JsonProperty("fullName") String fullName,
            @JsonProperty("groups") Collection<BigDBGroup> groups) {
        this.user = user;
        this.fullName = fullName;
        this.groups = ImmutableSet.copyOf(groups);
    }

    public String getUser() {
        return user;
    }

    public String getFullName() {
        return fullName;
    }

    public Set<BigDBGroup> getGroups() {
        return groups;
    }

    @JsonIgnore
    public boolean isAdmin() {
        return groups.contains(BigDBGroup.ADMIN);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fullName == null) ? 0 : fullName.hashCode());
        result = prime * result + ((groups == null) ? 0 : groups.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BigDBUser other = (BigDBUser) obj;
        if (fullName == null) {
            if (other.fullName != null)
                return false;
        } else if (!fullName.equals(other.fullName))
            return false;
        if (groups == null) {
            if (other.groups != null)
                return false;
        } else if (!groups.equals(other.groups))
            return false;
        if (user == null) {
            if (other.user != null)
                return false;
        } else if (!user.equals(other.user))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "BigDBUser [user=" + user + ", fullName=" + fullName + ", groups=" + groups
                + "]";
    }
}
