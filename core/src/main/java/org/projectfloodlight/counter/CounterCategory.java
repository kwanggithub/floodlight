package org.projectfloodlight.counter;

import java.util.Collection;
import java.util.TreeSet;

import org.projectfloodlight.db.data.annotation.BigDBProperty;

/**
 * A helper class to model a counter category which
 * has a name and contains a list of counter names under this category.
 * 
 * @author kevin.wang@bigswitch.com
 *
 */

public class CounterCategory {
    
    @BigDBProperty(value="name")
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    @BigDBProperty(value="sub-counter-name")
    public Collection<String> getCounterNames() {
        return counterNames;
    }
    public void setCounterNames(Collection<String> counterNames) {
        this.counterNames = counterNames;
    }
    
    public void addCounterName(String counterName) {
        if (this.counterNames == null) {
            counterNames = new TreeSet<String>();
        }
        counterNames.add(counterName);
    }
    
    private String name;
    private Collection<String> counterNames;
}