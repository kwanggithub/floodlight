package net.bigdb.auth.session;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import net.floodlightcontroller.util.IOUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** implements a restricted key/value store based on JSON files.
 *  Files are simply named '${key}.json'.
 *
 *  Thus, Keys are required to be 'safe' filenames (see safePattern, length < 122).
 *
 *  Values are serialized to JSON using Jackson. Objects are required to have appropriate
 *  JSON Annnotations.
 *
 *  Typically, a directory used by the JsonFileDataStore should not contain other files.
 *  It is possible to restrict the considered files by passing in a validPattern.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @param <T>
 */
public class JsonFileDataStore<T> {
    private final static Logger logger = LoggerFactory.getLogger(JsonFileDataStore.class);

    private final static String JSON_EXT = ".json";
    // 7 bit safe and account for the .json ext
    private final static int MAX_KEY_LENGTH = 127 - 5;
    // since we are using keys as filenames, can only accept filename safe chars
    private final static Pattern safePattern = Pattern.compile("^[a-zA-Z0-9_\\-]+$");

    // File System access/ locks
    private final File storeDir;

    // object mapping
    private final ObjectMapper objectMapper;

    private final Class<T> clazz;

    private final Pattern validPattern;

    /** Create a JsonFileDataStore
     * @param sessionDir directory where data is stored
     * @param clazz class to be stored / loaded
     * @param validPattern a Regexp pattern identifying valid file names for this store. Must match on
     *        the entire filename sans the '.json' suffix. Will be enforced <i>in addition</i> to the safePattern
     * @throws IOException
     */
    public JsonFileDataStore(File sessionDir, Class<T> clazz, Pattern validPattern)
            throws IOException {
        objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        this.storeDir = sessionDir;
        this.clazz = clazz;
        this.validPattern = validPattern;
        IOUtils.ensureDirectoryExistsAndWritable(sessionDir);
    }

    public boolean isKeyValid(String key) {
        if (key.length() > MAX_KEY_LENGTH)
            return false;
        else if (!safePattern.matcher(key).matches())
            return false;
        else if (!validPattern.matcher(key).matches())
            return false;
        else
            return true;
    }

    private File fileForKey(String key) throws IOException {
        if (!isKeyValid(key))
            throw new IOException("Invalid key specified");
        return new File(storeDir, key + JSON_EXT);
    }

    public boolean containsKey(String key) throws IOException {
        return fileForKey(key).exists();
    }

    public void remove(String key) throws IOException {
        File sessionFile = fileForKey(key);
        if (sessionFile.exists())
            if (!sessionFile.delete())
                throw new IOException("Could not delete file " + sessionFile);
    }

    /** save the given mapping to the store. Saves to ${file}.new and
     *  renames to reduce the chances of leaving behind an invalid file.
     *
     * @param key
     * @param value
     * @throws IOException
     */
    public void save(String key, T value) throws IOException {
        File sessionFile = fileForKey(key);
        File tmpFile = new File(sessionFile.getPath() + ".new");

        try (Writer writer = 
                Files.newWriter(tmpFile, Charset.forName("UTF-8"))) {
            objectMapper.writeValue(writer, value);
        }

        IOUtils.mvAndOverride(tmpFile, sessionFile);
    }

    public T load(String key) throws IOException {
        File sessionFile = fileForKey(key);
        
        try (Reader reader = 
                Files.newReader(sessionFile, Charset.forName("UTF-8"))) {
            return objectMapper.readValue(reader, clazz);
        }
    }

    private final FileFilter fileFilter = new FileFilter() {
        @Override
        public boolean accept(File file) {
            return file.exists() && file.isFile() && file.canRead()
                    && file.getName().endsWith(JSON_EXT)
                    && validPattern.matcher(keyForFile(file)).matches();
        }
    };

    public Iterable<String> listAllKeys() {
        List<String> res = new ArrayList<String>();
        for (File file : storeDir.listFiles(fileFilter)) {
            res.add(keyForFile(file));
        }
        return res;
    }

    private String keyForFile(File file) {
        String name = file.getName();
        String key = name.substring(0, name.length() - JSON_EXT.length());
        return key;
    }

    public int size() {
        return storeDir.listFiles(fileFilter).length;
    }

    public void deleteAll() {
        for (File file : storeDir.listFiles(fileFilter)) {
            if(!file.delete()) {
                logger.warn("Could not delete store file "+file);
            }
        }
    }
}
