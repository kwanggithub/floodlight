#
# middle ware.
#

import socket
import re

ip_address_match = re.compile(r'^(([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9][0-9]|2[0-4][0-9]|25[0-5])$')

def ip_address_to_controller_name(value, bigdb):
    if ip_address_match.match(value):
        name = socket.gethostbyaddr(value)
        if type(name) == tuple:
            return name[0]
    return value
