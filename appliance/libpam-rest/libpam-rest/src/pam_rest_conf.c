/**********************************************************************
 *
 * pam_rest_conf.c
 *
 * Intercept attempts to open the PAM configuration
 *
 **********************************************************************/

#define _GNU_SOURCE
/* allows us to use RTLD_NEXT */

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

#include <stdio.h>

#ifdef HAVE_DLFCN_H
#  include <dlfcn.h>
#endif

#ifdef HAVE_STDLIB_H
#  include <stdlib.h>
#endif

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#include <stdio.h>

#ifndef PAM_CONF
#  define PAM_CONF test_pam_rest.pam.conf
#endif

#define xstr(x) str(x)
#define str(x) #x

static void pam_rest_conf_init(void) __attribute__ ((constructor));

typedef FILE *(*fopen_fn_t)(const char *pathname, const char *mode);
static fopen_fn_t my_fopen;

void
pam_rest_conf_init(void)
{

  if (my_fopen != NULL)
    return;

  my_fopen = (fopen_fn_t) dlsym(RTLD_NEXT, "fopen");
  if (my_fopen == NULL)
    {
      fprintf(stderr,
              "%s:%s: *** cannot dlsym fopen\n",
              __FILE__, __FUNCTION__);
      exit(1);
    }

  if (my_fopen == fopen)
    {
      fprintf(stderr,
              "%s:%s: *** cannot interpose fopen\n",
              __FILE__, __FUNCTION__);
      exit(1);
    }

}

FILE *
fopen(const char *pathname, const char *mode)
{
  va_list ap;

  if (!strcmp(pathname, "/etc/pam.d/test_pam_rest"))
    {
      fprintf(stderr,
              "%s:%s: redirecting %s --> %s\n",
              __FILE__, __FUNCTION__, pathname, xstr(PAM_CONF));
      return my_fopen(xstr(PAM_CONF), mode);
    }

  return my_fopen(pathname, mode);
}
