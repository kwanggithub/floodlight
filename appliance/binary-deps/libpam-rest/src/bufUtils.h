/***********************************************************************
 *
 * bufUtils.h
 *
 **********************************************************************/

#ifndef BUFUTILS_H
#define BUFUTILS_H

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

#ifdef HAVE_STDLIB_H
#  include <stdlib.h>
#endif

typedef struct {
  char *start;
  char *end;
  size_t alloc;
} pam_buf_t;

extern void
bufInit(pam_buf_t *buf);

extern void
bufDestroy(pam_buf_t *buf);

extern size_t
bufAppend(pam_buf_t *buf, const char *s);

extern size_t
bufAppendSubstring(pam_buf_t *buf, const char *s, size_t sz);

#endif /* BUFUTILS_H */
