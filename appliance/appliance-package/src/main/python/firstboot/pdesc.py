#!/usr/bin/env python

PARAM_IP_MODE           = "IP Auto/Manual"
PARAM_HOSTNAME          = "Hostname"
PARAM_IP_ADDRESS        = "Local IP Address"
PARAM_IP_NETMASK        = "Netmask"
PARAM_IP_GATEWAY        = "Gateway"
PARAM_DNS               = "DNS Server"
PARAM_DOMAIN            = "DNS Search Domain"
PARAM_CLUSTER_OPTION    = "Cluster Option"
PARAM_NTP               = "NTP Server"
PARAM_MASTER_IP         = "Existing Node IP Address"
PARAM_MASTER_PASSWORD   = "Cluster Admin Password"
PARAM_RECOVERY_PASSWORD = "Emergency Recovery Password"

GROUP_LOCAL             = "Local Node Configuration"
GROUP_TIME              = "System Time"
GROUP_CLUSTER           = "Controller Clustering"

IP_STATIC               = "Static"
IP_DHCP                 = "DHCP"

ROLE_MASTER             = "Master"
ROLE_SLAVE              = "Slave"

NO_NTP                  = "NONTP"

# Only a root-level parameter would have a group field
# If a parameter does not have a group, it must have a parent
# If a parameter has a group, it must not have a parent

PARAMETERS = [
    #####################
    # Local node config #
    #####################
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_RECOVERY_PASSWORD,
        "prompt"        : "Password for emergency recovery user",
        "class"         : "PasswordParameter",
        "init-args"     : ["name", "prompt",],
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_IP_ADDRESS,
        "prompt"        : "IP address",
        "class"         : "LocalIpParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "0.0.0.0/0",
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_IP_NETMASK,
        "prompt"        : "Netmask",
        "class"         : "NetmaskParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "255.255.255.0",
        "refs"          : [PARAM_IP_ADDRESS,],
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_IP_GATEWAY,
        "prompt"        : "Default gateway address",
        "class"         : "GatewayParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "",
        "refs"          : [
                            PARAM_IP_ADDRESS,
                            PARAM_IP_NETMASK,
                          ],
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_DNS,
        "prompt"        : "DNS server address",
        "class"         : "DnsParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "",
        "refs"          : [PARAM_IP_ADDRESS,],
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_DOMAIN,
        "prompt"        : "DNS search domain",
        "class"         : "DomainParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "",
        "refs"          : [PARAM_IP_ADDRESS,],
    },
    {
        "group"         : GROUP_LOCAL,
        "name"          : PARAM_HOSTNAME,
        "prompt"        : "Hostname",
        "class"         : "HostnameParameter",
        "init-args"     : ["name", "prompt",],
        "refs"          : [PARAM_IP_ADDRESS,],
    },
    ##################
    # Cluster Params #
    ##################
    {
        "group"         : GROUP_CLUSTER,
        "name"          : PARAM_CLUSTER_OPTION,
        "prompt"        : "Please choose an option",
        "choices"       : [
                            {"label": "Start a new cluster", "value": ROLE_MASTER,},
                            {"label": "Join an existing cluster", "value": ROLE_SLAVE,},
                          ],
        "class"         : "ChoiceParameter",
        "init-args"     : ["name", "prompt", "choices",],
    },
    {
        "parent"        : PARAM_CLUSTER_OPTION,
        "parent-value"  : ROLE_SLAVE,
        "name"          : PARAM_MASTER_IP,
        "prompt"        : "Existing node IP",
        "class"         : "MasterIpParameter",
        "init-args"     : ["name", "prompt",],
        "refs"          : [
                            PARAM_IP_ADDRESS,
                            PARAM_IP_NETMASK,
                          ],
    },
    {
        "group"         : GROUP_CLUSTER,
        "name"          : PARAM_MASTER_PASSWORD,
        "prompt"        : "Administrator password for cluster",
        "class"         : "PasswordParameter",
        "init-args"     : ["name", "prompt",],
    },
    {
        "group"         : GROUP_TIME,
        "name"          : PARAM_NTP,
        "prompt"        : "Enter NTP server",
        "class"         : "NtpParameter",
        "init-args"     : ["name", "prompt",],
        "default"       : "0.bigswitch.pool.ntp.org",
        "refs"          : [PARAM_CLUSTER_OPTION,],
    }
]
