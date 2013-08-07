package org.projectfloodlight.db.auth.session;

import org.projectfloodlight.db.auth.BigDBUser;


/**
 * Interface contract of a SessionManager. A SessionManager manages effective mappings
 * betweeen session_ids/session_cookies and sessions.
 * <p>
 * Session ids are monotonically increasing integers. Implementations should
 * check for session id wraparounds to avoid attacks.
 * <p>
 * Session cookies are opaque, unguessable strings of significant entropy.
 * <p>
 * <b>NOTE:</b>SessionManger implementations are required to be thread-safe.
 */
public interface SessionManager {
    /**
     * create a session for the given user, and persist it in the
     * sessionManager.
     *
     * @param sessionData
     * @return the session id
     */
    public Session createSession(BigDBUser user);

    /**
     * return the active session associated with the given session id.
     *
     * @param sessionId
     * @return null if no active session is found
     */
    public Session getSessionForId(Long sessionId);

    /**
     * return the active session associated with the given session id.
     *
     * @param sessionId
     * @return null if no active session is found
     */
    public Session getSessionForCookie(String cookie);

    /**
     * remove the session identified by the given session id. NOOP if no such
     * session exists.
     *
     * @param sessionId
     * @return
     */
    public boolean removeSession(Session session);

    /**
     * Enumerate the session ids of active sessions in this session manager
     *
     * @return
     */
    public Iterable<Session> getActiveSessions();

    /** purge all sessions from the SessionManager
     */
    public void purgeAllSessions();
}
