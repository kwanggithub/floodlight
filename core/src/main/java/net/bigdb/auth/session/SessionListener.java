package net.bigdb.auth.session;

/** Listener callback for sessions. fired when a session has changed */
public interface SessionListener {
    public void onSessionTouched(Session session);
}
