package org.projectfloodlight.device.tag;
import org.junit.Test;
import org.projectfloodlight.device.tag.DeviceTag;
import org.projectfloodlight.device.tag.TagInvalidNameException;
import org.projectfloodlight.device.tag.TagInvalidNamespaceException;
import org.projectfloodlight.device.tag.TagInvalidValueException;
import org.projectfloodlight.test.FloodlightTestCase;

import static org.junit.Assert.*;

public class TagTest extends FloodlightTestCase {
    public static String ns = "com.namespace";
    public static String name = "name";
    public static String value = "value";
    
    @Test
    public void testTagCreation() {
        DeviceTag tag = null;
        String tagStr = null;
        try {
            tagStr = ns + "." + name;
            tag = new DeviceTag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tagStr = ns + "." + name + "=" + value;
            tag = new DeviceTag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidNameException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tag = new DeviceTag(null, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidNamespaceException);
            return;
        }
        assertTrue(tag == null);
        
        try {
            tagStr = ns + "." + name + " = " + value;
            tag = new DeviceTag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
        
        try {
            tagStr = ns + "." + name + "=" + value;
            tag = new DeviceTag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
        
        try {
            tagStr = ns + " . " + name + " = " + value;
            tag = new DeviceTag(tagStr, true);
        } catch (Exception e) {
            assertTrue(e instanceof TagInvalidValueException);
            return;
        }
        assertTrue(tag != null);
        assertTrue(tag.getNamespace().equals(ns));
        assertTrue(tag.getName().equals(name));
        assertTrue(tag.getValue().equals(value));
    }
}
