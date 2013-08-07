#!/bin/bash

function output {
    logger -t upgrade $1
    echo $1
}

function copy_virtual_ipaddress_config() {
    old_file="$1"
    new_file="$2"
    tmp_file="tmp_copy_virtual_ipaddress_config"
    if (egrep -q "^\s*virtual_ipaddress" $old_file)
    then
        sed -ne '0,/^vrrp_instance/p' $new_file > $tmp_file
        sed -ne '/^\s*virtual_ipaddress/,$p' $old_file | sed -ne '0,/^[^#!]*}/p' >> $tmp_file
        sed -ne '/^vrrp_instance/,$p' $new_file | tail -n +2 >> $tmp_file
        rm $new_file
        mv $tmp_file $new_file
    fi
}

function replace_config() {
    old_file="$1"
    new_file="$2"
    field_name="$3"
    new_value=`grep $field_name $old_file || :`
    echo $new_value $field_name
    sed -i "s/^\s*$field_name\s*.*$/$new_value/" $new_file
}

function add_config() {
    new_file="$1"
    field_name="$2"
    line_before="$3"
    output "Check $field_name in $new_file"
    if (egrep -q "$field_name" $new_file)
    then
        output "$field_name already present"
    else
        output "Adding $field_name"
        sed -i "s/$line_before/$line_before\n  $field_name/" $new_file
    fi
}

function remove_config() {
    new_file="$1"
    pattern="$2"
    start_line="$3"
    end_line="$4"
    line=`grep -n "$pattern" $new_file || :`
    if [ -n "$line" ]
    then
        output "Removing $pattern"
        n=`echo $line | cut -d: -f1 -`
        start=`expr $n + $start_line`
        end=`expr $n + $end_line`
        echo "$pattern,$start,$end"
        sed -i "`expr $start`,`expr $end`d" $new_file
    fi
}

function copy_virtual_ipaddress_config() {
    old_file="$1"
    new_file="$2"
    tmp_file="tmp_copy_virtual_ipaddress_config"
    if grep -q "virtual_ipaddress" $old_file
    then
        echo -e "$(sed -ne '0,/ha-notify-role/p' $new_file)" > $tmp_file
        echo -e "$(sed -ne '/virtual_ipaddress/,$p' $old_file | sed -ne '0,/}/p')" >> $tmp_file
        echo -e "$(sed -ne '/ha-notify-role/,$p' $new_file | tail -n +2)" >> $tmp_file
        rm $new_file
        mv $tmp_file $new_file
    fi
}

set -e -x -o pipefail

export PATH=/sbin:/usr/sbin:$PATH
UPGRADE_PKG=$1
[ -f ${UPGRADE_PKG} ] || { echo "${UPGRADE_PKG} not found"; exit 1; }
shift

configtype="boot-config"
if [ -f /opt/bigswitch/run/boot-config ]; then
    configtype=`grep "^boot-config=" /opt/bigswitch/run/boot-config | head -1 | cut -s -d= -f2`
fi

##HA upgrade stuff
CONTROLLER_ID=
CLUSTER_SIZE=
NETADDR=
SEED=""
MASTERIP=""
UPGRADED_IPS=""
NON_UPGRADED_IPS=""
UPGRADE_TYPE=""
HAS_MULTIPLE_IF=""

if [ "$configtype"x != "boot-config"x ]; then
    output "Initializing HA Upgrade"

    CONTROLLER_ID=`grep "^controller-id=" /opt/bigswitch/run/boot-config | head -1 | cut -s -d= -f2`
    output "CONTROLLER_ID="$CONTROLLER_ID
    HAS_MULTIPLE_IF=`curl -X GET http://localhost/rest/v1/model/controller-interface 2>/dev/null | (grep "|Ethernet|1" || :) | wc -l`
    output "HAS_MULTIPLE_IF="$HAS_MULTIPLE_IF
    CLUSTER_SIZE=`curl -X GET http://localhost/rest/v1/model/controller-node 2>/dev/null | awk -F , '{count = 0; for (i=1; i <= NF; i++) if ($i ~ /"status"/) count++; print count}'`
    output "cluster size = ${CLUSTER_SIZE}"
    NETADDR=$(ifconfig eth0 | grep 'inet addr:' | cut -f2 -d: | cut -f1 -d' ')
    MASTERIP=`/opt/bigswitch/sys/bin/get_master_node`
    UPGRADE_TYPE="upgrade-as-master-node"
    for ip in `/opt/bigswitch/sys/bin/get_upgraded_nodes localhost`; do
        if [ $ip == $NETADDR ]; then
            output "node already upgraded"
            exit 2;
        fi
        cid=`/opt/bigswitch/sys/bin/controllerid_by_ip localhost $ip`
        UPGRADED_IPS=${UPGRADED_IPS}"$ip:$cid,"
        UPGRADE_TYPE="upgrade-as-slave-node"
        SEED=${SEED}"$ip,"
    done
    if [ $UPGRADE_TYPE == "upgrade-as-master-node" ]; then
        SEED=$NETADDR
    fi
    for ip in `/opt/bigswitch/sys/bin/get_ready_nodes localhost`; do
    if [ $ip != $NETADDR ]; then
        cid=`/opt/bigswitch/sys/bin/controllerid_by_ip localhost $ip`
        NON_UPGRADED_IPS=${NON_UPGRADED_IPS}"$ip:$cid,"
    fi
    done
    output "MASTER at $MASTERIP"
    output "UPGRADE_TYPE=$UPGRADE_TYPE"
    output "UPGRADED_IPS=$UPGRADED_IPS"
    output "NON_UPGRADED_IPS=$NON_UPGRADED_IPS"
    output "SEED=$SEED"
fi

PART_NUM="[0-9]"

output "Finding partition for installation."

# Find the /sysboot partition (e.g. /dev/sda1)
SYSBOOT_DEV=$(awk '/^[^ ]+ \/sysboot / { print $1; exit }' /proc/mounts)
[ "${SYSBOOT_DEV}" ] || { output "/sysboot not mounted"; exit 1; }

# Assume the disk device is the /sysboot partition minus the partition number (e.g. /dev/sda)
DISK_DEV=$(expr match ${SYSBOOT_DEV} '\(/dev/[a-z]*\)')
ROOTDEV=$(df -P / | tail -n 1 | awk '/.*/ { print $1 }')

# Search /sysboot/extlinux.conf for the partition to install into
label=
PART_DEV=
while read l; do
    set $l
    if [ "$1" = LABEL ]; then
        if expr match $2 "^${PART_NUM}\$" >/dev/null; then
            label=$2
        else
            label=
        fi  
    fi
    if [ "${label}" ] && [ "$1" = APPEND ] && [ ${DISK_DEV}$3 != ${ROOTDEV} ]; then
        PART_NUM=${label}
        PART_DEV=${DISK_DEV}$3
    break
    fi  
done </sysboot/extlinux.conf
[ "${PART_DEV}" ] || { output "Cannot find unused partition ${PART_NUM}"; exit 1; }

# get a list of disabled services, if any

output "Setting up services."

ORIG_DISABLED_SERVICES=`ls /etc/init/*.disabled 2>/dev/null || true`

RELEASE=$(unzip -p ${UPGRADE_PKG} release)
output "Installing ${RELEASE} into partition ${PART_NUM} (${PART_DEV})"

# Copy filesystem image into partition
unzip -p ${UPGRADE_PKG} rootfs | pcopy -s /dev/stdin ${PART_DEV}
/sbin/tune2fs ${PART_DEV} -U random
/sbin/e2fsck -fy ${PART_DEV}
/sbin/resize2fs ${PART_DEV}

output "Copying system configuration to partition ${PART_NUM}"
# Copy host configuration, install bootloader in new partition
(
d=$(mktemp -d)
trap "set +e; umount $d/sys; umount $d/proc; umount $d/dev/pts; umount $d/dev; umount $d/log; umount $d; rmdir $d" EXIT
mount ${PART_DEV} $d
mkdir -p $d/log
mount --bind /log $d/log
mount --bind /dev $d/dev
mount --bind /dev/pts $d/dev/pts
mount -t proc proc $d/proc
mount -t sysfs sysfs $d/sys
tar -c --absolute-names --ignore-failed-read /etc/network/interfaces /etc/resolv.conf /etc/ssh /etc/hostname /etc/timezone /etc/localtime /etc/ntp.conf /etc/passwd /etc/shadow /etc/ssl /etc/keepalived /etc/rsyslog-filters.conf /etc/nsswitch.conf /etc/pam.d/sshd /etc/pam.d/login /opt/bigswitch/run /opt/bigswitch/feature /opt/bigswitch/statd/lc.runtime /home/bsn/.ssh >$d/config.tar
cp /opt/bigswitch/db/conf/cassandra.yaml $d/opt/bigswitch/db/conf/cassandra.yaml
rm -f /opt/bigswitch/run/upgrade-config

/usr/sbin/chroot $d /bin/bash -e -o pipefail <<EOF
set -x
sed "/^bsn:/d;/^admin:/d;/^images:/d;/^root:/d;/^recovery:/d" /etc/passwd >/etc/passwd.orig
sed "/^bsn:/d;/^admin:/d;/^images:/d;/^root:/d;/^recovery:/d" /etc/shadow >/etc/shadow.orig
tar -x --absolute-names --overwrite --no-overwrite-dir -f config.tar
sed -i "s/^127\.0\.1\.1 .*\$/127.0.1.1 \$(cat /etc/hostname 2>/dev/null || :)/" /etc/hosts
grep -e "^bsn:" -e "^admin:" -e "^images:" -e "^root:" -e "^recovery:" /etc/passwd >>/etc/passwd.orig
mv /etc/passwd.orig /etc/passwd
grep -e "^bsn:" -e "^admin:" -e "^images:" -e "^root:" -e "^recovery:" /etc/shadow >>/etc/shadow.orig
mv /etc/shadow.orig /etc/shadow
/usr/sbin/update-grub >&/dev/null
/usr/sbin/grub-install --force ${PART_DEV} >&/dev/null
echo "LABEL=sysboot /sysboot ext2 ro 0 0" >>/etc/fstab
echo "LABEL=log /log ext4 defaults 0 0" >>/etc/fstab
echo "LABEL=swap swap swap defaults 0 0" >>/etc/fstab
CURR_DISABLED_SERVICES=\`ls /etc/init/*.disabled 2>/dev/null || true\`
for SERVICE in \${CURR_DISABLED_SERVICES} ; do
    if ! echo "${ORIG_DISABLED_SERVICES}" | grep "\${SERVICE}" -q ; then
        mv "\${SERVICE}" /etc/init/\`basename "\${SERVICE}" .disabled\`
    fi
done
mkdir -p /log/bigswitch
[ -e /log/sys ] && rm -rf /var/log || mv /var/log /log/sys; ln -s /log/sys /var/log
[ -e /log/bigswitch/floodlight ] && rm -rf /opt/bigswitch/floodlight/log || mv /opt/bigswitch/floodlight/log /log/bigswitch/floodlight; ln -s /log/bigswitch/floodlight /opt/bigswitch/floodlight/log
[ -e /log/bigswitch/db ] && rm -rf /opt/bigswitch/db/log || mv /opt/bigswitch/db/log /log/bigswitch/db; ln -s /log/bigswitch/db /opt/bigswitch/db/log
[ -e /log/bigswitch/con ] && rm -rf /opt/bigswitch/con/log || mv /opt/bigswitch/con/log /log/bigswitch/con; ln -s /log/bigswitch/con /opt/bigswitch/con/log
[ -e /log/bigswitch/statd ] && rm -rf /opt/bigswitch/statd/log || mv /opt/bigswitch/statd/log /log/bigswitch/statd; ln -s /log/bigswitch/statd /opt/bigswitch/statd/log
[ -e /log/bigswitch/statdropd ] && rm -rf /opt/bigswitch/statdropd/log || mv /opt/bigswitch/statdropd/log /log/bigswitch/statdropd; ln -s /log/bigswitch/statdropd /opt/bigswitch/statdropd/log
[ -e /log/bigswitch/syncd ] && rm -rf /opt/bigswitch/syncd/log || mv /opt/bigswitch/syncd/log /log/bigswitch/syncd; ln -s /log/bigswitch/syncd /opt/bigswitch/syncd/log
[ -e /log/bigswitch/firstboot ] && rm -rf /opt/bigswitch/firstboot/log || mv /opt/bigswitch/firstboot/log /log/bigswitch/firstboot; ln -s /log/bigswitch/firstboot /opt/bigswitch/firstboot/log
[ -e /log/bigswitch/keepalived ] && rm -rf /opt/bigswitch/keepalived/log || mv /opt/bigswitch/keepalived/log /log/bigswitch/keepalived; ln -s /log/bigswitch/keepalived /opt/bigswitch/keepalived/log
[ -e /log/bigswitch/discover-ip ] && rm -rf /opt/bigswitch/discover-ip/log || mv /opt/bigswitch/discover-ip/log /log/bigswitch/discover-ip; ln -s /log/bigswitch/discover-ip /opt/bigswitch/discover-ip/log
[ -e /log/bigswitch/packetstreamer ] && rm -rf /opt/bigswitch/packetstreamer/log || mv /opt/bigswitch/packetstreamer/log /log/bigswitch/packetstreamer; ln -s /log/bigswitch/packetstreamer /opt/bigswitch/packetstreamer/log
chown -R syslog:adm /log/bigswitch /var/log/auth.log /var/log/syslog /var/log/kern.log /var/log/mail.log
chmod 640 /log/bigswitch/*/* || :
EOF

# update keepalived.conf with existing config values
output "Updating keepalived configure ${PART_NUM}"
old_file="$d/etc/keepalived/keepalived.conf"
if [ -e $old_file ] ; then
    new_file="$d/etc/keepalived/keepalived.conf.orig.tmp"
    # backup the orig conf
    output "copy orignal conf file template to $new_file"
    cp "$d/etc/keepalived/keepalived.conf.orig" $new_file
    output "Copying keepalived configuration from $old_file to $new_file ${PART_NUM}"
    replace_config $old_file $new_file "state"
    replace_config $old_file $new_file "interface"
    replace_config $old_file $new_file "virtual_router_id"
    replace_config $old_file $new_file "priority"
    replace_config $old_file $new_file "advert_int"
    copy_virtual_ipaddress_config $old_file $new_file
    # Don't track eth0 link status
    add_config $new_file "dont_track_primary" "interface eth0"
    # Remove obsolete failover_check scripts
    remove_config $new_file "vrrp_script failover_check" 0 4
    remove_config $new_file "failover_check" -1 1
    # Modify healthcheck arg and interval
    sed -i "s/check.sh 120 5/check.sh/" $new_file
    sed -i "s/interval 120/interval 2/" $new_file
    output "Backup $old_file to $old_file.old ${PART_NUM}"
    mv $old_file "$old_file.old"
    output "copy $new_file to $old_file ${PART_NUM}"
    mv $new_file $old_file
    # end of update keepalived.conf
else
    output "No $old_file ${PART_NUM}, skipping keepalived configure"
fi

if [ "$configtype"x != "boot-config"x ]; then
    /usr/sbin/chroot $d /bin/bash -e -o pipefail <<EOF
set -x
echo "upgrade-boot=true" >> /opt/bigswitch/run/boot-config
echo "upgrade-type=$UPGRADE_TYPE" >> /opt/bigswitch/run/boot-config
echo "cluster-size=$CLUSTER_SIZE" >> /opt/bigswitch/run/boot-config
echo "upgraded-nodes=$UPGRADED_IPS" >> /opt/bigswitch/run/boot-config
echo "nonupgraded-nodes=$NON_UPGRADED_IPS" >> /opt/bigswitch/run/boot-config
echo "has-multiple-if=$HAS_MULTIPLE_IF" >> /opt/bigswitch/run/boot-config
curl -X PUT -d '{"status":"Upgrading"}' http://localhost/rest/v1/model/controller-node?id=${CONTROLLER_ID} 1>/dev/null 2>&1
sed -i.old "s/- seeds: .*/- seeds: \"$SEED\"/" /opt/bigswitch/db/conf/cassandra.yaml
# Configure the floodlight configuration property files to
# use the correct controller ID.
FLOODLIGHT_CONFIG_DIR=/opt/bigswitch/floodlight/configuration
FLOODLIGHT_PROVIDER_NAME=org.projectfloodlight.core.FloodlightProvider
for name in \
    bigfloodlight \
    bigtapconfig \
    reactiveflowonlyconfig
do
    echo -e "\norg.projectfloodlight.core.FloodlightProvider.controllerid=$CONTROLLER_ID" >> /opt/bigswitch/floodlight/configuration/\${name}.properties
done
EOF
fi
)

echo
output "${RELEASE} successfully installed into partition ${PART_NUM} (${PART_DEV})"

if [ "$configtype"x != "boot-config"x ]; then
    echo "revert-boot=true" >> /opt/bigswitch/run/boot-config
    CLUSTER_NODES=""
    for ip in `/opt/bigswitch/sys/bin/get_cluster_nodes localhost`; do
       CLUSTER_NODES=${CLUSTER_NODES}"$ip,"
    done
    echo "cluster-nodes=$CLUSTER_NODES" >> /opt/bigswitch/run/boot-config
fi

# Set newly installed partition as the default, update boot menu
(
mount -o remount,rw /sysboot
trap "set +e; mount -o remount,ro /sysboot" EXIT
sed -i "s:^DEFAULT .*:DEFAULT ${PART_NUM}:" /sysboot/extlinux.conf
[ -e /sysboot/menu.txt ] || echo -e "\n1   ?\n2   ?\n\nPress Space within 3 seconds to interrupt automatic boot\n" >/sysboot/menu.txt
sed -i "s:^\([0-9]\) [* ] \(.*\):\1   \2:;s:^${PART_NUM}   .*:${PART_NUM} * ${RELEASE}:" /sysboot/menu.txt
)

output "Partition ${PART_NUM} will be used next time the system is rebooted"

