/*
    Copyright (C) 2001,2002,2009,2010,2012 Bernhard R. Link <brlink@debian.org>

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 2 as
    published by the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    Please tell me, if you find errors or mistakes.

Based on parts of the GNU C Library:

   Common code for file-based database parsers in nss_files module.
   Copyright (C) 1996, 1997, 1998, 1999, 2000 Free Software Foundation, Inc.
   This file is part of the GNU C Library.

   The GNU C Library is free software; you can redistribute it and/or
   modify it under the terms of the GNU Lesser General Public
   License as published by the Free Software Foundation; either
   version 2.1 of the License, or (at your option) any later version.

   The GNU C Library is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
   Lesser General Public License for more details.
*/

#define _GNU_SOURCE 1

#include <stdio.h>
#include <stdlib.h>
#include <stdarg.h>
#include <nss.h>
#include <pwd.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>
#include <errno.h>
#include <ctype.h>

#include "s_config.h"

enum nss_status _nss_remoteuser_getpwuid_r(uid_t, struct passwd *, char *, size_t, int *);
enum nss_status _nss_remoteuser_setpwent(void);
enum nss_status _nss_remoteuser_endpwent(void);
enum nss_status _nss_remoteuser_getpwnam_r(const char *, struct passwd *, char *, size_t, int *);
enum nss_status _nss_remoteuser_getpwent_r(struct passwd *, char *, size_t, int *);

static enum nss_status p_search(FILE *f, const char *name, const uid_t uid, struct passwd *pw, int *errnop, char *buffer, size_t buflen);

static inline enum nss_status p_search(FILE *f, const char *name, const uid_t uid, struct passwd *pw, int *errnop, char *buffer, size_t buflen) {
#define SANEQUIT {funlockfile(stream); if (f==NULL) fclose(stream);}
#define TOCOLON(p, h) { while (*p && *p != ':') \
				p++; \
			h=p; \
			if(!*p) { \
				SANEQUIT \
				*errnop = 0; \
				return NSS_STATUS_UNAVAIL; \
			} \
			p++; \
			*h='\0'; \
			h--; \
			}
	FILE *stream = f;
	char *p, *h;
	uid_t t_uid;
	gid_t t_gid;
	char *t_name, *t_passwd, *t_gecos, *t_shell, *t_dir;

	if (stream == NULL) {
		stream = fopen("/etc/passwd", "r");
		if (stream == NULL) {
			*errnop = errno;
			return NSS_STATUS_UNAVAIL;
		}
	}
	flockfile(stream);
	while (1) {
		buffer[buflen - 1] = '\xff';
		p = fgets_unlocked(buffer, buflen, stream);
		if (p == NULL) {
			if (feof_unlocked(stream)) {
				SANEQUIT
				*errnop = ENOENT;
				return NSS_STATUS_NOTFOUND;
			} else {
				*errnop = errno;
				SANEQUIT
				return NSS_STATUS_UNAVAIL;
			}
		}
		/* hm, indexing is not safe here if the line is too long */
		if (buffer[buflen - 1] != '\xff') {
			SANEQUIT
			*errnop = ERANGE;
			return NSS_STATUS_TRYAGAIN;
		}
		h = index(p, '\n');
		if (h == NULL) {
			SANEQUIT
			*errnop = ERANGE;
			return NSS_STATUS_TRYAGAIN;
		}
		while (isspace(*h) && h != p) {
			*h = '\0';
			h--;
		}
		/* Ignore comments */
		if (*p == '#')
			continue;
		/* extract name */
		while (isspace(*p))
			++p;
		/* Ignore empty lines */
		if (*p == '\0')
			continue;
		t_name = p;
		TOCOLON(p, h);
		if (name && strcmp(name, t_name)!=0)
			continue;
		/* passwd (should be "x" or "!!" or something...) */
		while (isspace(*p))
			++p;
		t_passwd = p;
		TOCOLON(p, h);
		/* extract uid */
		t_uid = strtol(p, &h, 10);
		if (*h != ':') {
			SANEQUIT
			*errnop = 0;
			return NSS_STATUS_UNAVAIL;
		}
		if (uid != 0 && uid != t_uid) {
			continue;
		}
		p = ++h;
		/* extract gid */
		t_gid = strtol(p, &h, 10);
		if (*h != ':') {
			SANEQUIT
			*errnop = 0;
			return NSS_STATUS_UNAVAIL;
		}
		p = ++h;
		/* extract gecos */
		while (isspace(*p))
			++p;
		t_gecos = p;
		TOCOLON(p, h);
		/* extract dir */
		while (isspace(*p))
			++p;
		t_dir = p;
		TOCOLON(p, h);
		/* extract shell */
		while (isspace(*p))
			++p;
		t_shell = p;
		if (index(p, ':') != NULL) {
			SANEQUIT
			*errnop = 0;
			return NSS_STATUS_UNAVAIL;
		}

		SANEQUIT
		*errnop = 0;
		pw->pw_name = t_name;
		pw->pw_uid = t_uid;
		pw->pw_passwd = t_passwd;
		pw->pw_gid = t_gid;
		pw->pw_gecos = t_gecos;
		pw->pw_dir = t_dir;
		pw->pw_shell = t_shell;
		return NSS_STATUS_SUCCESS;
	}
}

enum nss_status _nss_remoteuser_getpwuid_r(uid_t uid, struct passwd *result, char *buf, size_t buflen, int *errnop) {
        int code;
        struct passwd localuser;
        char *localname;
        size_t sz;

        /*
         * XXX roth -- extra safe here, do not use the const name
         * in the return value
         */
        buf[buflen-1] = '\0';
        strncpy(buf, REMOTEUSER, buflen);
        if (buf[buflen-1] != '\0') {
                *errnop = ERANGE;
                return NSS_STATUS_TRYAGAIN;
        }
        localname = buf;
        sz = strlen(buf) + 1;
        buf += sz;
        buflen -= sz;

        *errnop = 0;
        if (uid == REMOTEUID) {
                code = p_search(NULL, LOCALUSER, 0, &localuser, errnop, buf, buflen);
                if (code != NSS_STATUS_SUCCESS)
                        return code;

		result->pw_name = localname;
		result->pw_uid = localuser.pw_uid;
		result->pw_passwd = "x";
		result->pw_gid = localuser.pw_gid;
		result->pw_gecos = "Remotely authenticated user";
		result->pw_dir = localuser.pw_dir;
		result->pw_shell = localuser.pw_shell;
                return NSS_STATUS_SUCCESS;
        } else {
                return NSS_STATUS_NOTFOUND;
        }
}

enum nss_status _nss_remoteuser_getpwnam_r(const char *name, struct passwd *result, char *buf, size_t buflen, int *errnop) {
        int code;
        struct passwd localuser;
        struct passwd remoteuser;
        size_t sz;
        char *localname;
        
        *errnop = 0;

        /*
         * XXX roth -- extra safe here, do not use the const name
         * in the return value
         */
        buf[buflen-1] = '\0';
        strncpy(buf, name, buflen);
        if (buf[buflen-1] != '\0') {
                *errnop = ERANGE;
                return NSS_STATUS_TRYAGAIN;
        }

        /* switch to caller's storage */
        localname = buf;

        /* continue to use the rest of the buffer */
        sz = strlen(buf) + 1;
        buf += sz;
        buflen -= sz;

        /*
         * XXX roth -- test if remote username exists in /etc/passwd,
         * return NSS_STATUS_NOTFOUND if it exists locally
         */
        code = p_search(NULL, name, 0, &remoteuser, errnop, buf, buflen);
        switch (code) {
        case NSS_STATUS_SUCCESS:
                /* user exists locally --> do not authorize from remote */
                return NSS_STATUS_NOTFOUND;
        case NSS_STATUS_NOTFOUND:
                /* local entry missing -- ideal */
                break;
        default:
                return code;
        }

        /* XXX roth -- find the LOCALUSER (e.g. 'bsn') entry in /etc/passwd */
        code = p_search(NULL, LOCALUSER, 0, &localuser, errnop, buf, buflen);
        if (code != NSS_STATUS_SUCCESS)
                return code;

        /* XXX roth -- construct a passwd entry here
         * copy over fields from the standard local user
         */
        result->pw_name = localname;
        result->pw_uid = REMOTEUID;
        result->pw_passwd = "x";
        result->pw_gid = localuser.pw_gid;
        result->pw_gecos = "Remotely authenticated user";
        result->pw_dir = localuser.pw_dir;
        result->pw_shell = localuser.pw_shell;
        return NSS_STATUS_SUCCESS;
}

/* setpwent always succeeds, we are not otherwise allowed to enumerate
 * remote users
 */
enum nss_status _nss_remoteuser_setpwent(void) {
        return NSS_STATUS_SUCCESS;
}

/* endpwent always succeeds, we are not otherwise allowed to enumerate
 * remote users
 */
enum nss_status _nss_remoteuser_endpwent(void) {
        return NSS_STATUS_SUCCESS;
}

/* getpwent always fails (not found), we are not otherwise allowed to enumerate
 * remote users
 */
enum nss_status _nss_remoteuser_getpwent_r(struct passwd *pw, char *buffer, size_t buflen, int *errnop) {
	*errnop = 0;
        return NSS_STATUS_NOTFOUND;
}

/* Local variables: */
/* mode: c */
/* c-file-style: "linux" */
/* End: */
