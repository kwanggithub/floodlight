package org.sdnplatform.os;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.sdnplatform.os.IOSActionlet.ActionType;
import org.sdnplatform.os.IOSConfiglet.ConfigType;
import org.sdnplatform.os.model.ActionApplyType;
import org.sdnplatform.os.model.ConfigApplyType;
import org.sdnplatform.os.model.OSAction;
import org.sdnplatform.os.model.OSConfig;
import org.sdnplatform.os.model.OSModel;

import com.google.common.base.Objects;

/**
 * Abstract base class convenient for implementing platform providers
 * @author readams
 *
 */
public abstract class AbstractPlatformProvider implements IPlatformProvider {

    // ************************
    // AbstractPlatformProvider
    // ************************

    /**
     * Get a collection of configlets to apply to the configuration
     * @return a {@link Collection} of {@link IOSConfiglet}
     */
    protected abstract Collection<IOSConfiglet> getConfiglets();
    
    /**
     * Get a collection of actionlets to apply to the system
     * @return a {@link Collection} of {@link IOSActionlet}
     */
    protected abstract Collection<IOSActionlet> getActionlets();

    // *****************
    // IPlatformProvider
    // *****************
    
    @Override
    public abstract boolean isApplicable(File basePath);

    @Override
    public WrapperOutput applyConfiguration(File basePath,
                                            OSConfig oldConfig,
                                            OSConfig newConfig) {
        WrapperOutput overall = new WrapperOutput();
        if (oldConfig == null) {
            return applyConfiglets(basePath, getConfiglets(), 
                                   null, newConfig);
        } else {
            EnumMap<ConfigType, List<IOSConfiglet>> cmap = getCMap();
            EnumSet<ConfigType> changes = EnumSet.noneOf(ConfigType.class);
            findChanges(changes, OSConfig.class,
                        oldConfig, newConfig, null);
            for (ConfigType type : changes) {
                if (!cmap.containsKey(type)) continue;
                overall.add(applyConfiglets(basePath, cmap.get(type), 
                                            oldConfig, newConfig));
            }
        }
        return overall;
    }

    @Override
    public WrapperOutput applyAction(File basePath, OSAction action) {
        WrapperOutput overall = new WrapperOutput();
        Map<ActionType, List<IOSActionlet>> amap = getAMap();
        EnumSet<ActionType> changes = EnumSet.noneOf(ActionType.class);
        findActions(changes, OSAction.class, action, null);
        
        for (ActionType type : changes) {
            if (!amap.containsKey(type)) continue;
            overall.add(applyActionlets(basePath, amap.get(type), action));
        }
        return overall;
    }
    
    // *************
    // Local methods
    // *************

    private WrapperOutput applyActionlets(File basePath,
                                          List<IOSActionlet> alets,
                                          OSAction action) {
        WrapperOutput overall = new WrapperOutput();
        for (IOSActionlet alet : alets) {
            overall.add(alet.applyAction(basePath, action));
        }
        return overall;
    }

    private WrapperOutput applyConfiglets(File basePath,
                                          Collection<IOSConfiglet> clets,
                                          OSConfig oldConfig,
                                          OSConfig newConfig) {
        WrapperOutput overall = new WrapperOutput();
        for (IOSConfiglet configlet : clets) {
            overall.add(configlet.applyConfig(basePath,
                                              oldConfig, newConfig));
        }
        return overall;
    }

    protected static void findActions(EnumSet<ActionType> actions,
                                      Class<?> fieldType,
                                      Object action,
                                      ActionType[] defaultApplyType) {
        try {
            BeanInfo bi = Introspector.getBeanInfo(fieldType);

            PropertyDescriptor[] props = bi.getPropertyDescriptors();
            for (PropertyDescriptor prop : props) {
                if ("class".equals(prop.getName())) continue;
                
                Method getter = prop.getReadMethod();
                ActionApplyType atype = 
                        getter.getAnnotation(ActionApplyType.class);
                ActionType[] propApplyType = null;
                if (atype != null) propApplyType = atype.value();
                if (propApplyType == null)
                    propApplyType = defaultApplyType;
                if (propApplyType == null)
                    propApplyType = new ActionType[] {};
                
                Class<?> type = prop.getPropertyType();

                Object v = null;
                if (action != null)
                    v = getter.invoke(action, (Object[])null);
                
                boolean hasAction = false;
                
                if (OSModel.class.isAssignableFrom(type)) {
                    findActions(actions, type, v, propApplyType);
                } else if (type.isArray()) {
                    if (v != null &&
                        OSModel.class.isAssignableFrom(type.getComponentType())) {
                        OSAction[] acts = (OSAction[]) v;
                        for (int i = 0; i < acts.length; i++) {
                            findActions(actions, type.getComponentType(), 
                                        acts[i], propApplyType);
                        }
                    } else {
                        hasAction = (v != null);
                    }
                } else {
                    hasAction = (v != null);                    
                }
                
                if (hasAction) {
                    actions.addAll(Arrays.asList(propApplyType));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected static void findChanges(EnumSet<ConfigType> changes,
                                      Class<?> fieldType,
                                      Object oldConfig, Object newConfig,
                                      ConfigType[] defaultApplyType) {
        try {
            BeanInfo bi = Introspector.getBeanInfo(fieldType);

            PropertyDescriptor[] props = bi.getPropertyDescriptors();
            for (PropertyDescriptor prop : props) {
                if ("class".equals(prop.getName())) continue;
                
                Method getter = prop.getReadMethod();
                ConfigApplyType atype = 
                        getter.getAnnotation(ConfigApplyType.class);
                ConfigType[] propApplyType = null;
                if (atype != null) propApplyType = atype.value();
                if (propApplyType == null)
                    propApplyType = defaultApplyType;
                if (propApplyType == null)
                    propApplyType = new ConfigType[] {};

                Class<?> type = prop.getPropertyType();

                Object oldv = null;
                Object newv = null;
                if (oldConfig != null)
                    oldv = getter.invoke(oldConfig, (Object[])null);
                if (newConfig != null)
                    newv = getter.invoke(newConfig, (Object[])null);
               
                boolean changed = false;

                if (OSModel.class.isAssignableFrom(type)) {
                    findChanges(changes, type, oldv, newv, propApplyType);
                } else if (type.isArray()) {
                    if (OSModel.class.isAssignableFrom(type.getComponentType())) {
                        OSModel[] oldm = (OSModel[])oldv;
                        OSModel[] newm = (OSModel[])newv;
                        if ((oldm == null && newm != null) ||
                            (oldm != null && newm == null)){
                            changed = true;
                        } else if (oldm != null && newm != null) {
                            if (oldm.length != newm.length) {
                                changed = true;
                            } else {
                                for (int i = 0; i < newm.length; i++) {
                                    findChanges(changes, type.getComponentType(), 
                                                oldm[i], newm[i], propApplyType);
                                }
                            }
                        }
                    } else if (type.getComponentType().isPrimitive()) {
                        changed = !Objects.equal(oldv, newv);
                    } else {
                        changed = !Arrays.deepEquals((Object[])oldv, (Object[])newv);
                    }
                } else {
                    changed = !Objects.equal(oldv, newv);
                }
                if (changed) {
                    for (ConfigType t : propApplyType) {
                        changes.add(t);
                    }
                }
            }
        } catch (ReflectiveOperationException | IntrospectionException e) {
            e.printStackTrace();
        }
    }
    
    private EnumMap<ConfigType, List<IOSConfiglet>> getCMap() {
        EnumMap<ConfigType, List<IOSConfiglet>> cmap = 
                new EnumMap<ConfigType, List<IOSConfiglet>>(ConfigType.class);
        Collection<IOSConfiglet> clets = getConfiglets();
        for (IOSConfiglet clet : clets) {
            EnumSet<ConfigType> p = clet.provides();
            for (ConfigType t : p) {
                List<IOSConfiglet> pcs = cmap.get(t);
                if (pcs == null) {
                    cmap.put(t, pcs = new ArrayList<IOSConfiglet>());
                }
                pcs.add(clet);
            }
        }
        return cmap;
    }

    private EnumMap<ActionType, List<IOSActionlet>> getAMap() {
        EnumMap<ActionType, List<IOSActionlet>> cmap = 
                new EnumMap<ActionType, List<IOSActionlet>>(ActionType.class);
        Collection<IOSActionlet> clets = getActionlets();
        for (IOSActionlet clet : clets) {
            EnumSet<ActionType> p = clet.provides();
            for (ActionType t : p) {
                List<IOSActionlet> pcs = cmap.get(t);
                if (pcs == null) {
                    cmap.put(t, pcs = new ArrayList<IOSActionlet>());
                }
                pcs.add(clet);
            }
        }
        return cmap;
    }
}
