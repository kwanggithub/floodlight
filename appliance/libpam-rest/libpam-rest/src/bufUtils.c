/**********************************************************************
 *
 * bufUtils.c
 *
 **********************************************************************/

#ifdef HAVE_CONFIG_H
#  include "config.h"
#endif

#ifdef HAVE_STRING_H
#  include <string.h>
#endif

#include "bufUtils.h"

void
bufInit(pam_buf_t *buf)
{
  buf->start = NULL;
  buf->end = NULL;
  buf->alloc = 0;
}

void
bufDestroy(pam_buf_t *buf)
{
  if (buf->start)
    free(buf->start);
}

size_t
bufAppendSubstring(pam_buf_t *buf, const char *s, size_t sz)
{
  /* initialize buf for the first time */
  if (buf->start == NULL)
    {
      size_t alloc = sz;
      if (alloc < 1024)
        alloc = 1024;

      buf->alloc = alloc;
      buf->start = (char *) malloc(buf->alloc);
      if (buf->start == NULL)
        return 0;
      buf->end = buf->start;
      buf->start[0] = '\0';
    }

  /* extend buf to fit new text */
  if (((buf->end - buf->start) + sz + 1) > buf->alloc)
    {
      buf->alloc *= 2;
      char *start = realloc(buf->start, buf->alloc);
      char *end = start + (buf->end-buf->start);
      if (start == NULL)
        return 0;
      buf->start = start;
      buf->end = end;
    }

  strncpy(buf->end, (const char *) s, sz);
  buf->end += sz;
  *(buf->end) = '\0';

  return sz;
}

size_t
bufAppend(pam_buf_t *buf, const char *s)
{
  size_t sz = strlen(s);
  return bufAppendSubstring(buf, s, sz);
}
