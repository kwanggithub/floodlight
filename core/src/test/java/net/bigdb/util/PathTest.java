package net.bigdb.util;

import net.bigdb.util.Path;

import org.junit.Test;

import junit.framework.TestCase;

public class PathTest extends TestCase {
    
    private void testPath(Path path, Path.Type expectedType,
            String... expectedComponents) {
        
        // Check length and type
        assertEquals(expectedComponents.length, path.size());
        assertEquals(expectedType, path.getType());
        
        // Check components
        for (int i = 0; i < expectedComponents.length; i++) {
            assertEquals(expectedComponents[i], path.get(i));
        }
        
        // Check iterator
        int i = 0;
        for (String component: path) {
            assertEquals(expectedComponents[i], component);
            i++;
        }
        
        // Check that it throws exception on out of bounds index
        try {
            path.get(expectedComponents.length);
            fail("Path has too many components");
        }
        catch (IndexOutOfBoundsException exc) {
            // Expected exception
        }
    }
    
    private void testPathString(String pathString, char separator,
            Path.Type expectedType, String... expectedComponents) {
        Path path = new Path(pathString, separator);
        testPath(path, expectedType, expectedComponents);
    }
    
    private void testPathString(String pathString,
            Path.Type expectedType, String... expectedComponents) {
        Path path = new Path(pathString);
        testPath(path, expectedType, expectedComponents);
    }
    
    @Test
    public void testPaths() {
        testPath(new Path(), Path.Type.RELATIVE);
        testPathString("", Path.Type.RELATIVE);
        testPathString("/", Path.Type.ABSOLUTE);
        testPathString("foo", Path.Type.RELATIVE, "foo");
        testPathString("foo/bar", Path.Type.RELATIVE, "foo","bar");
        testPathString("foo/bar/", Path.Type.RELATIVE, "foo","bar");
        testPathString("/root/foo/bar/", Path.Type.ABSOLUTE, "root","foo","bar");
        testPathString("a/b/c/def/ghisdlkjlskjdf", Path.Type.RELATIVE,
                "a","b","c","def","ghisdlkjlskjdf");
        testPathString("abc:def:234567", ':', Path.Type.RELATIVE,
                "abc", "def", "234567");
        testPathString("abc:def:234567", ':', Path.Type.RELATIVE,
                "abc", "def", "234567");
    }

    @Test
    public void testMultiplePathConstructor() {
        Path path = new Path(new Path("/foo"), new Path("abc"), new Path("xyz"));
        testPath(path, Path.Type.ABSOLUTE, "foo", "abc", "xyz");
        path = new Path(new Path("a"), new Path("b/c/d"), new Path("x/y/z"));
        testPath(path, Path.Type.RELATIVE, "a", "b", "c", "d", "x", "y", "z");
    }

    @Test
    public void testSubPath() {
        Path path = new Path("/abc/def/ghi/xyz");
        testPath(path.getSubPath(0), Path.Type.ABSOLUTE, "abc", "def", "ghi", "xyz");
        testPath(path.getSubPath(1), Path.Type.RELATIVE, "def", "ghi", "xyz");
        testPath(path.getSubPath(1,2), Path.Type.RELATIVE, "def");
        testPath(path.getSubPath(2, 4), Path.Type.RELATIVE, "ghi", "xyz");
        path = new Path("abc/def/ghi");
        testPath(path.getSubPath(0,2), Path.Type.RELATIVE, "abc", "def");
        testPath(path.getSubPath(1), Path.Type.RELATIVE, "def", "ghi");
    }

    @Test
    public void testChildPath() {
        Path path = new Path("/abc");
        testPath(path.getChildPath(""), Path.Type.ABSOLUTE, "abc");
        testPath(path.getChildPath("def"), Path.Type.ABSOLUTE, "abc", "def");
        testPath(path.getChildPath("def/ghi"), Path.Type.ABSOLUTE, "abc", "def", "ghi");
        path = new Path("abc");
        testPath(path.getChildPath("def"), Path.Type.RELATIVE, "abc", "def");
        testPath(Path.ROOT_PATH.getChildPath("abc"), Path.Type.ABSOLUTE, "abc");
    }
}
