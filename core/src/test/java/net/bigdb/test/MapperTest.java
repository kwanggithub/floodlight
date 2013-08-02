package net.bigdb.test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.*;
import net.bigdb.util.StringUtils;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
public class MapperTest {
    protected File dumpDirectory;
    protected ObjectMapper mapper;
    {
        mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        
        Map<String, String> env = System.getenv();
        String dumpDirectoryName =
                env.get("BIGDB_REST_UNIT_TEST_DUMP_DIRECTORY");
        if (dumpDirectoryName != null && !dumpDirectoryName.isEmpty()) {
            dumpDirectory = new File(dumpDirectoryName);
            dumpDirectory.mkdirs();
        }
    }

    protected void checkExpectedResult(Object object,
                                       String testName) throws Exception {
    
        String actualText = mapper.writeValueAsString(object);

        String nameExpected = testName + ".expected";
        if (dumpDirectory != null) {
            File outputFile = new File(dumpDirectory, nameExpected);
            
            try (BufferedWriter outputWriter = 
                    Files.newWriter(outputFile, StandardCharsets.UTF_8)) {
                outputWriter.write(actualText);
            }
        }
        InputStream expectedInputStream =
                getClass().getResourceAsStream(nameExpected);
        if (expectedInputStream == null) {
            throw new Exception(String.format(
                    "Missing expected result file: \"%s\"", nameExpected));
        }
        String expectedText =
                StringUtils.readStringFromInputStream(expectedInputStream);

        // We create two JsonNode objects here and compare them.
        // This makes it check that the actual JSON is equivalent
        // instead of just the strings being exactly the same.
        JsonNode expectedJsonNode = mapper.readTree(expectedText);
        JsonNode actualJsonNode = mapper.readTree(actualText);
        assertEquals(expectedJsonNode, actualJsonNode);
    }

    @Test
    public void testMockup() {
        
    }
}
