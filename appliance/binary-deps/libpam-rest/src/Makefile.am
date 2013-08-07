######################################################################
#
# src/Makefile.am
#
######################################################################

INCLUDES			= -I$(top_builddir)/config

moduledir			= /lib/security
module_LTLIBRARIES		= pam_rest.la

pam_rest_la_SOURCES		= \
  pam_rest.h \
  pam_rest.c \
  bufUtils.h \
  bufUtils.c \
  pamUtils.h \
  pamUtils.c \
  # THIS LINE INTENTIONALLY LEFT BLANK

##pam_rest_la_CFLAGS		= $(AM_CFLAGS) -Ilibtac/include
pam_rest_la_LDFLAGS		= -module -avoid-version -Wl,--no-undefined
pam_rest_la_LIBADD		= -ljson -lcurl -lpam -lpam_misc

TESTS				= test_pam_rest.py
noinst_PROGRAMS			= test_pam_rest
noinst_LTLIBRARIES		= libpam_rest_conf.la
noinst_DATA			= \
  test_pam_rest.pam.conf \
  config.py \
  # THIS LINE INTENTIONALLY LEFT BLANK

libpam_rest_conf_la_SOURCES	= \
  pam_rest_conf.c \
  # THIS LINE INTENTIONALLY LEFT BLANK

# make sure to set -rpath to force a solib
libpam_rest_conf_la_LDFLAGS	= \
  -module -avoid-version \
  -Wl,--no-undefined \
  -rpath $(abs_builddir)/.libs \
  # THIS LINE INTENTIONALLY LEFT BLANK

libpam_rest_conf_la_LIBADD	= -lc -ldl

pam_rest_conf.lo: CPPFLAGS = -DPAM_CONF="$(abs_builddir)/test_pam_rest.pam.conf"

# debian fakeroot also plays with LD_PRELOAD
TESTS_ENVIRONMENT		= \
  abs_srcdir=$(abs_srcdir) \
  abs_builddir=$(abs_builddir) \
  # THIS LINE INTENTIONALLY LEFT BLANK

if COWBUILDER

check-am: check-cowbuilder-install

else

TESTS_ENVIRONMENT		+= \
  LD_PRELOAD=$(abs_builddir)/.libs/libpam_rest_conf.so \
  # THIS LINE INTENTIONALLY LEFT BLANK

endif

test_pam_rest_SOURCES		= \
  test_pam_rest.c \
  # THIS LINE INTENTIONALLY LEFT BLANK

test_pam_rest_LDADD		= -lpam

test_pam_rest.pam.conf: Makefile
	/bin/cp /dev/null $@
	echo "auth required $(abs_builddir)/.libs/pam_rest.so uri=http://localhost:8000/api/v1/auth method=POST post_type=json debug" >> $@
	echo "account required $(abs_builddir)/.libs/pam_rest.so debug" >> $@

check-cowbuilder-install: pam_rest.la
	$(mkinstalldirs) /etc/pam.d
	$(INSTALL_DATA) test_pam_rest.pam.conf /etc/pam.d/test_pam_rest

config.py: Makefile
	/bin/cp /dev/null $@
	echo "COOKIE_NAME = \"\"\"@COOKIE_NAME@\"\"\";" >> $@
	echo "ENV_NAME = \"\"\"@ENV_NAME@\"\"\";" >> $@
	echo "REST_NAME = \"\"\"@REST_NAME@\"\"\";" >> $@

CLEANFILES			= \
  test_pam_rest.pam.conf \
  config.py \
  # THIS LINE INTENTIONALLY LEFT BLANK
