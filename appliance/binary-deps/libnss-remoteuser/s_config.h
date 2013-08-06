/* 
 * LOCALUSER
 *
 * This is a user in /etc/passwd that is used as an authz template
 * for remote users (shell, gid, home directory)
 *
 * If this user does not exist, then remote (authn/authz) logins will
 * not work.
 *
 */
#define LOCALUSER "admin"

/*
 * REMOTEUSER
 *
 * Remote user's name in NSS.
 * Note that we are not tracking unique remote users;
 * they all have the same privileges.
 *
 * NOTE that it is important that this user *NOT*
 * be in the passwd file, otherwise pam_unix would short-circuit
 * remote authz schemes.
 *
 */
#define REMOTEUSER "remoteuser"

/*
 * REMOTEUID
 *
 * UID shared by all remote users.
 * This UID should not already be in the passwd file,
 * though I am not 100% clear on how bad that would be.
 *
 */

#define REMOTEUID 10000
