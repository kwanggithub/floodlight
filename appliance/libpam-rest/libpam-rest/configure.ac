dnl
dnl Adapted from configure.ac in libpam-tacplus
dnl
dnl ---------------------------------------------------------------------------

AC_PREREQ(2.59)
AC_COPYRIGHT([
THIS LICENSE INTENTIONALLY LEFT BLANK
])
AC_INIT(pam_rest, 0.0, [carl.roth@bigswitch.com])
AC_CONFIG_AUX_DIR(config)
AC_CONFIG_MACRO_DIR(config)
AM_INIT_AUTOMAKE
AC_CONFIG_SRCDIR([src/pam_rest.h])
AC_CONFIG_HEADER([config/config.h])

dnl --------------------------------------------------------------------
dnl Checks for programs.
AC_PROG_CC
AC_PROG_INSTALL
AC_PROG_LN_S
AC_PROG_MAKE_SET
AM_PROG_LIBTOOL

dnl --------------------------------------------------------------------
dnl Checks for libraries.
ac_save_libs=$LIBS
AC_CHECK_LIB(pam, pam_start)
LIBS=$ac_save_libs
AC_CHECK_LIB(curl, curl_global_init)
LIBS=$ac_save_libs
dnl AC_[]CHECK_LIB(yajl, yajl_parse)
AC_CHECK_LIB(json, json_tokener_parse)
LIBS=$ac_save_libs

dnl
dnl Figure out which json-c library we have
dnl
AC_CHECK_FUNC([json_tokener_get_error],[found10=yes],[found10=no])

AC_MSG_CHECKING([for json_tokener_errors symbol])

LIBS="$LIBS -ljson"
AC_LINK_IFELSE([
  AC_LANG_PROGRAM([#include <json/json.h>],
                  [const char **errors = json_tokener_errors;])
               ],
               [found09=yes],[found09=no])
LIBS=$ac_save_LIBS
if test "$found09" = "yes"; then
  AC_MSG_RESULT([yes])
else
  AC_MSG_RESULT([no])
fi

AC_MSG_CHECKING([for json-c API version])
if test "$found10" = "yes"; then
  AC_MSG_RESULT([found json-c 0.10 or later])
  AC_DEFINE([HAVE_JSON_C_10], [1], [json-c API v0.10 or later])
elif test "$found09" = "yes"; then
  AC_MSG_RESULT([found json-c 0.9 or earlier])
  AC_DEFINE([HAVE_JSON_C_09], [1], [json-c API v0.9 or earlier])
else
  AC_MSG_RESULT([unknown])
  AC_MSG_ERROR([cannot identify json-c])
fi

dnl --------------------------------------------------------------------
dnl Checks for header files.
AC_HEADER_STDC
AC_CHECK_HEADERS([stdlib.h unistd.h string.h alloca.h stdarg.h dlfcn.h fcntl.h arpa/inet.h netdb.h])
dnl AC_[]CHECK_HEADERS([fcntl.h netinet/in.h stdlib.h string.h strings.h sys/socket.h sys/time.h syslog.h unistd.h])

dnl --------------------------------------------------------------------
dnl Checks for typedefs, structures, and compiler characteristics.
AC_C_CONST
dnl AC_[]TYPE_SIZE_T
dnl AC_[]HEADER_TIME

dnl --------------------------------------------------------------------
dnl Checks for library functions.
AC_FUNC_REALLOC
dnl AC_[]FUNC_SELECT_ARGTYPES
dnl AC_[]TYPE_SIGNAL
dnl AC_[]CHECK_FUNCS([bzero gethostbyname gettimeofday inet_ntoa select socket])

AC_MSG_CHECKING([for cowbuilder environment])
found=no
case "$SUDO_COMMAND" in
  *cowbuilder*) found=yes ;;
esac
AC_MSG_RESULT([$found])
AM_CONDITIONAL([COWBUILDER], [test "$found" = "yes"])

AC_SUBST([COOKIE_NAME], ["session_cookie"])
AC_DEFINE_UNQUOTED([COOKIE_NAME], ["$COOKIE_NAME"],
                   [Cookie name for authentication token])

AC_SUBST([REST_NAME], ["session_cookie"])
AC_DEFINE_UNQUOTED([REST_NAME], ["$REST_NAME"],
                   [Rest tag for authentication token])

AC_SUBST([ENV_NAME], ["FL_SESSION_COOKIE"])
AC_DEFINE_UNQUOTED([ENV_NAME], ["$ENV_NAME"],
                   [PAM Environment variable name for authentication token])

dnl --------------------------------------------------------------------
dnl Generate made files
AC_CONFIG_FILES([Makefile src/Makefile config/Makefile debian/Makefile])
AC_OUTPUT

dnl
dnl Local variables:
dnl mode: sh
dnl End:
dnl
