package net.bigdb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.TestCase;

import org.junit.Test;

public class FileLocatorTest extends TestCase {

    private File ROOT_TEST_DATA_DIRECTORY =
            new File("src/test/resources/net/bigdb/util/filelocator");
    
    private void performTest(FileLocator locator, String name,
            String expectedPath) throws IOException {
        InputStream actualFileIS = locator.findFile(name);
        if (actualFileIS != null) {
            File expectedFile = new File(ROOT_TEST_DATA_DIRECTORY, expectedPath);
            BufferedReader br = 
                    new BufferedReader(new InputStreamReader(actualFileIS));
            File actualFile = new File(ROOT_TEST_DATA_DIRECTORY, br.readLine());
            assertEquals(expectedFile.getCanonicalPath(), 
                         actualFile.getCanonicalPath());
        } else {
            assertNull("Error locating file", expectedPath);
        }
    }
    
    private void addSearchPath(FileLocator locator, String relativePath,
            boolean recursive) {
        File directory = new File(ROOT_TEST_DATA_DIRECTORY, relativePath);
        locator.addSearchPath(directory, recursive);
    }
    
    @Test
    public void testRecursive() throws IOException {
        FileLocator locator = new FileLocator();
        addSearchPath(locator, "", true);

        performTest(locator, "Test1.txt", "Test1.txt");
        performTest(locator, "Test2.txt", "Test2.txt");
        performTest(locator, "Test3.txt", "sub1/Test3.txt");
        performTest(locator, "Test4.txt", "sub2/Test4.txt");
        performTest(locator, "Test5.txt", "sub2/Test5.txt");
        performTest(locator, "Test6.txt", "sub2/sub3/Test6.txt");
        performTest(locator, "Test7.txt", null);
    }
    
    @Test
    public void testNonrecursive() throws IOException {
        FileLocator locator = new FileLocator();
        addSearchPath(locator, "", false);
        
        performTest(locator, "Test1.txt", "Test1.txt");
        performTest(locator, "Test2.txt", "Test2.txt");
        performTest(locator, "Test3.txt", null);
        performTest(locator, "Test4.txt", null);
        performTest(locator, "Test5.txt", null);
        performTest(locator, "Test6.txt", null);
        performTest(locator, "Test7.txt", null);
    }
    
    @Test
    public void testMultipleSearchPaths() throws IOException {
        FileLocator locator = new FileLocator();
        addSearchPath(locator, "sub1", true);
        addSearchPath(locator, "sub2", true);

        performTest(locator, "Test1.txt", "sub1/Test1.txt");
        performTest(locator, "Test2.txt", null);
        performTest(locator, "Test3.txt", "sub1/Test3.txt");
        performTest(locator, "Test4.txt", "sub2/Test4.txt");
        performTest(locator, "Test5.txt", "sub2/Test5.txt");
        performTest(locator, "Test6.txt", "sub2/sub3/Test6.txt");
        performTest(locator, "Test7.txt", null);
    }
    
    @Test
    public void testInvalidSearchPath() throws IOException {
        FileLocator locator = new FileLocator();
        addSearchPath(locator, "does-not-exist", true);
        addSearchPath(locator, "", true);

        performTest(locator, "Test1.txt", "Test1.txt");
        performTest(locator, "Test2.txt", "Test2.txt");
        performTest(locator, "Test3.txt", "sub1/Test3.txt");
        performTest(locator, "Test4.txt", "sub2/Test4.txt");
        performTest(locator, "Test5.txt", "sub2/Test5.txt");
        performTest(locator, "Test6.txt", "sub2/sub3/Test6.txt");
        performTest(locator, "Test7.txt", null);
    }
}
