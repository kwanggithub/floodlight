import fcntl
import termios
import struct
import os

import utif

#
# --------------------------------------------------------------------------------
#
def get_terminal_size():
    """
    Return (rows, columns) associated with the terminal session
    """
    def ioctl_GWINSZ(fd):
        try:
            cr = struct.unpack('hh', fcntl.ioctl(fd, termios.TIOCGWINSZ,
                                                 '1234'))
        except:
            return None
        return cr
    cr = ioctl_GWINSZ(0) or ioctl_GWINSZ(1) or ioctl_GWINSZ(2)
    if not cr:
        try:
            fd = os.open(os.ctermid(), os.O_RDONLY)
            cr = ioctl_GWINSZ(fd)
            os.close(fd)
        except:
            pass
    if not cr:
        try:
            cr = (os.environ['LINES'], os.environ['COLUMNS'])
        except:
            cr = (24, 80)

    if (cr[1] == 0 or cr[0] == 0):
        return (80, 24)

    return int(cr[1]), int(cr[0])

#
# --------------------------------------------------------------------------------
#
def choices_text_builder(matches, max_len = None, col_width = None):
    """
    @param max_len integer, describes max width of any entry in matches
    @param col_width integer, colun width
    """
    # Sort the choices alphabetically, but if the token ends
    # in a number, sort that number numerically.
    try:
        entries = sorted(matches, utif.completion_trailing_integer_cmp)
    except Exception, e:
        traceback.print_exc()

    if col_width == None:
        # request to look it up
        (col_width, line_length) = get_terminal_size()
        col_width = min(120, col_width)

    if max_len == None:
        # request to compute the max length
        max_len = len(max(matches, key=len))

    count = len(matches)
    max_len += 1 # space after each choice
    if max_len > col_width:
        # one per line?
        pass
    else:
        per_line = col_width / max_len
        lines = (count + (per_line - 1)) / per_line
        if lines == 1:
            return ''.join(['%-*s' % (max_len, m) for m in entries])
        else:
            # fill the columns, which means skipping around the entries
            result = []
            for l in range(lines):
                result.append(['%-*s' % (max_len, entries[i])
                               for i in range(l, count, lines)])
            lines = [''.join(x) for x in result]
            return '\n'.join(lines)
