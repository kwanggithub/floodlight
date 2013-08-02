#
#
# feature registry.

import os

import bsn_constants
import debug

def init_feature(context):
    global feature_registry, feature_registry_initialized

    feature_registry = {}
    feature_registry_initialized = {}

    register_feature('experimental',
                     feature_enabled_via_file, None,
                     {'file' : 'experimental'})

    register_feature('ha',
                     feature_enabled_via_boot_config, None, {})

    register_feature('ha-cluster',
                     feature_enabled_ha_cluster, None,
                     { 'context' : context })

    register_feature('ha-master',
                     feature_enabled_ha_master, None, 
                     { 'contet' : context })

    register_feature('ha-slave',
                     feature_enabled_ha_slave, None,
                     { 'contet' : context })

    register_feature('single-controller',
                     feature_enabled_single_controller, None, 
                     { 'context' : context })


#
# --------------------------------------------------------------------------------
# feature_enabled_via_file
#
def feature_enabled_via_file(feature, file = None):
    """
    Use a file in feature directory to determine if a feature is enabled.
    """
    path = os.path.join('/opt/bigswitch/feature', file)
    return os.path.exists(path)


#
# --------------------------------------------------------------------------------
# feature_enabled_via_boot_config
#
def feature_enabled_via_boot_config(feature):
    try:
        with open(bsn_constants.boot_config_filename, "r") as f:
            for line in f:
                if feature.startswith('ha') and line.startswith('ha-config='):
                    parts = line.split('=') 
                    if len(parts) > 0 and parts[1] == 'enabled\n':
                        return True
    except:
        pass
    return False

#
# --------------------------------------------------------------------------------
# feature_enabled_ha_cluster
#
def feature_enabled_ha_cluster(feature, context):
    """
    Return True if ha is enabled, AND when more than one controller-node
    is defined.
    """
    if feature_enabled_via_boot_config(feature):
        print 'Need to determine the number of nodes in the cluster'
    return False

#
# --------------------------------------------------------------------------------
# feature_enabled_ha_master
#
def feature_enabled_ha_master(feature, context):
    # ??? does this only apply when a cluster is active?
    if feature_enabled_ha_cluster(feature):
        # MASTER or MASTER-BLOCKED
        if context.cached_current_role().startswith('MASTER'):
            return True
    return False

#
# --------------------------------------------------------------------------------
# feature_enabled_ha_slave
#
def feature_enabled_ha_slave(feature, context):
    # ??? does this only apply when a cluster is active?
    if feature_enabled_ha_cluster(feature):
        if context.cached_current_role() == 'SLAVE':
            return True
    return False

#
# --------------------------------------------------------------------------------
# feature_enabled_single_controller
#
def feature_enabled_single_controller(feature, context):
    return not feature_enabled_ha_cluster('ha-cluster', context)


#
# --------------------------------------------------------------------------------
# register_feature
#
def register_feature(feature_name, enabled, initialize, args = None):

    """
    Describe a feature, associate a method to determine if the
    named feaure it enabled.
    @param feature_name feature group name ('bvs', 'bigtap', etc)
    @param enabled function to call to test for feature enablement
    @param initialize function to call at first time 'enabled' is requested
    @param args arguments to all called functions
    """
    feature_registry[feature_name] = (enabled, initialize, args)

#
# --------------------------------------------------------------------------------
# feature_enabled
#
def feature_enabled(feature):
    """
    Return True when a particular feature is enabled.
    @param feature name of the feature ('bvs', 'bigtap', 'ha', etc)
    """

    if feature in feature_registry:
        (enabled, initializer, args) = feature_registry[feature]
        # initialized?
        if initializer and not feature in feature_registry_initialized:
            feature_registry_initialized[feature] = True
            (initializer)(featrue, **args)

        return (enabled)(feature, **args)

    if debug.cli() or debug.description():
        print 'feature_enabled: missing configuration for:', feature
    return False

