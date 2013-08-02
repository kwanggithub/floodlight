package net.floodlightcontroller.device.tag;
import org.junit.Test;
import static org.junit.Assert.*;


import net.floodlightcontroller.device.tag.DeviceTag;
import net.floodlightcontroller.device.tag.TagInvalidNameException;
import net.floodlightcontroller.device.tag.TagInvalidNamespaceException;
import net.floodlightcontroller.device.tag.TagInvalidValueException;
import net.floodlightcontroller.test.FloodlightTestCase;

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
