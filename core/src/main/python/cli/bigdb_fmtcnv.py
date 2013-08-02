import os
import bigdb
import fmtcnv
import time
import datetime
import pytz
import midw
from pytz import reference

def bigdb_bandwidth(value, schema, detail):
    if value == 0:
        return '-'
    return fmtcnv.print_bit_rate(value)

bigdb.bigdb_type_formatter_register('bandwidth', bigdb_bandwidth)


def bigdb_bandwidth_bytes_per_sec(value, schema, detail):
    if value == 0:
        return '-'
    return fmtcnv.print_byte_rate(value)

bigdb.bigdb_type_formatter_register('bandwidth_bytes_per_sec', bigdb_bandwidth_bytes_per_sec)


def uptime_millisec_formatter(value, schema, detail):
    return fmtcnv.print_timesince_msec_since(value)

bigdb.bigdb_type_formatter_register('uptime_milliseconds', uptime_millisec_formatter)


def bigdb_decode_flow_cookie(value, schema, detail):
    return fmtcnv.decode_flow_cookie(value)

bigdb.bigdb_type_formatter_register('flow-cookie', bigdb_decode_flow_cookie)


def bigdb_decode_ether_type(value, schema, detail):
    ether_type = fmtcnv.ether_type_to_name_dict.get(value, value)
    if detail == 'details':
        return '%s (%s)' % (ether_type, value)
    return ether_type

bigdb.bigdb_type_formatter_register('ether-type', bigdb_decode_ether_type)


def bigdb_switch_port_feature(value, schema, detail):
    if value == 0:
        return '-'
    return fmtcnv.decode_port_features(value)

bigdb.bigdb_type_formatter_register('switch-port-feature', bigdb_switch_port_feature)


def bigdb_switch_port_flags(value, schema, detail):
    # manages both state-flags, and config-flags.

    name = schema['name']
    if name == 'state-flags':
        if value & 1:
            flags = 'down'
        else:
            flags = 'up'
    else:
        flags = ''
    if value & 2: # 2^1 = 2
        flags += ',blocked'
    if name == 'config-flags':
        if value & 4: # 2^2 = 4
            flags += ',drop-recv'
    if name == 'state-flags':
        if value & 4: # 2^2 = 4
            flags += ',live-ffg'
    if name == 'state-flags':
        if value & 8: # 2^3 = 8
            flags += ',blocked'
    else:
        if value & 8: # 2^3 = 8
            flags += ',no-stp'
    if value & 8:
        flags += ',blocked'
    if value & 32: # 2^5 = 32
        flags += ',drop-forw'
    if value & 64: # 2^6 = 64
        flags += ',no-packet-in'

    if flags != '' and detail == 'details':
        return '%s (%#x)' % (flags, value)
    return flags

bigdb.bigdb_type_formatter_register('switch-port-flags', bigdb_switch_port_flags)



def bigdb_iso_8601_date(value, schema, detail):
    # iso_8601 time stamps, in a string.
    utc_time = datetime.datetime(*(time.strptime(value, "%Y-%m-%dT%H:%M:%S.%fZ")[0:6]), tzinfo = pytz.utc)
    local_zone = reference.LocalTimezone()
    this_time = utc_time.astimezone(local_zone)
    return this_time.strftime("%Y-%m-%d %H:%M:%S %Z")

bigdb.bigdb_type_formatter_register('date-and-time', bigdb_iso_8601_date)


def bigdb_session_auth_token(value, schema, detail):
    cookie = os.environ.get('BSC_SESSION_COOKIE')
    if cookie == value:
        return '*'
    return ''

bigdb.bigdb_type_formatter_register('session-auth-token', bigdb_session_auth_token)


# Several of the OF counter values from switches are intended to
# set the value to '-1' to indicate N/A.   For a list of these
# fields, see the OF 1.0 doc, page 5, table 4.
#
def bigdb_of_counter64(value, schema, detail):
    if value == -1:
        return 'N/A'
    return value

bigdb.bigdb_type_formatter_register('of-counter64', bigdb_of_counter64)


def bigdb_ip_address(value, schema, detail):
    # mask types should not be ip-addresses. BSC-3925
    if schema['name'].find('mask') != -1:
        return value

    nice_value = midw.ip_address_to_controller_name(value)
    if nice_value == None:
        return value
    if detail == 'details':
        return '%s (%s)' % (nice_value, value)
    return nice_value

bigdb.bigdb_type_formatter_register('ip-address', bigdb_ip_address)
