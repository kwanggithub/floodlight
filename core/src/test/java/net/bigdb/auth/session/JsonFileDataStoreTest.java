package net.bigdb.auth.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import net.floodlightcontroller.util.IOUtils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class JsonFileDataStoreTest {

    private File storeDir;
    private Pattern validPattern;
    private ObjectMapper objectMapper;

    @Before
    public void setup() throws IOException {
        storeDir = Files.createTempDir();
        validPattern = Pattern.compile("hello\\d+");
        objectMapper = new ObjectMapper();
    }

    private JsonFileDataStore<String> store() throws IOException {
        JsonFileDataStore<String> store = new JsonFileDataStore<String>(storeDir, String.class, validPattern);
        return store;
    }

    @After
    public void teardown() throws IOException {
        IOUtils.deleteRecursively(storeDir);
    }

    @Test
    public void testAllKeys() throws IOException {
        JsonFileDataStore<String> store = store();
        assertEquals(0, store.size());
        assertTrue(Iterables.isEmpty(store.listAllKeys()));

        Set<String> expected = new HashSet<String>();
        store.save("hello1", "this is a test");
        expected.add("hello1");
        assertEquals(expected, FluentIterable.from(store.listAllKeys()).toSet());
        store.save("hello1", "this is a test");
        assertEquals(expected, FluentIterable.from(store.listAllKeys()).toSet());
        store.save("hello2", "this is also a test");
        expected.add("hello2");
        assertEquals(expected, FluentIterable.from(store.listAllKeys()).toSet());
    }

    @Test
    public void testSaveSizes() throws IOException {
        JsonFileDataStore<String> store = store();
        assertEquals(0, store.size());
        store.save("hello1", "this is a test");
        assertEquals(1, store.size());
        store.save("hello1", "this is a test");
        assertEquals(1, store.size());
        store.save("hello2", "this is also a test");
        assertEquals(2, store.size());
    }

    @Test
    public void testRemove() throws IOException {
        JsonFileDataStore<String> store = store();
        store.save("hello1", "this is a test");
        assertTrue(fileForKey("hello1").exists());
        store.remove("hello1");
        assertFalse(fileForKey("hello1").exists());
    }

    @Test
    public void testSimpleSave() throws IOException {
        JsonFileDataStore<String> store = store();
        store.save("hello1", "this is a test");
        assertEquals(readJsonString("hello1"), "this is a test");
    }

    @Test
    public void testUnsafeKeys() throws IOException {
        JsonFileDataStore<String> store = new JsonFileDataStore<String>(storeDir, String.class, Pattern.compile(".*"));
        String[] invalidKeys = new String[] { "/", "../../etc/shadow", "a/b/c", "", "\u1234", ".a.b" };
        for(String k: invalidKeys) {
            try {
                store.save(k, "test");
                fail("Exception expected when saving to invalid key "+k);
            } catch(IOException e) {
                // expected
            }
        }
    }

    @Test
    public void testInvalidKeys() throws IOException {
        JsonFileDataStore<String> store = store();
        String[] invalidKeys = new String[] { "hello", "hello1b", "hallo", "1b" };
        for(String k: invalidKeys) {
            try {
                store.save(k, "test");
                fail("Exception expected when saving to invalid key "+k);
            } catch(IOException e) {
                // expected
            }
        }
    }

    @Test
    public void testSaveAndReload() throws IOException {
        JsonFileDataStore<String> store = store();
        store.save("hello1", "this is a test");
        String data = store.load("hello1");
        assertEquals("this is a test", data);
    }

    private String readJsonString(String key) throws JsonParseException, JsonMappingException, IOException {
        return objectMapper.readValue(fileForKey(key), String.class);
    }

    private File fileForKey(String key) {
        return new File(storeDir, key + ".json");
    }
}
