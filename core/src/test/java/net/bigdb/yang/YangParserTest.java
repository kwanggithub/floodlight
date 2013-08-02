package net.bigdb.yang;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.Map;

import static org.junit.Assert.*;
import net.bigdb.util.StringUtils;
import net.bigdb.yang.parser.YangLexer;
import net.bigdb.yang.parser.YangParser;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.junit.Test;

public class YangParserTest {

    protected static File dumpDirectory = null;

    static {
        Map<String, String> env = System.getenv();
        String dumpDirectoryName = env.get("YANG_UNIT_TEST_DUMP_DIRECTORY");
        if (dumpDirectoryName != null && !dumpDirectoryName.isEmpty()) {
            dumpDirectory = new File(dumpDirectoryName);
            dumpDirectory.mkdirs();
        }
    }

    public void testYangFile(String name) throws Exception {
        InputStream inputYang = getClass().getResourceAsStream(name + ".yang");
        // FIXME: Should we use an explicit character encoding here?
        ANTLRInputStream input = new ANTLRInputStream(inputYang);
        YangLexer lexer = new YangLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        YangParser parser = new YangParser(tokens);

        // Parse the top-level module statement
        ModuleStatement moduleStatement = parser.moduleStatement();

        String actualText = moduleStatement.toString();

        String nameExpected = name + ".expected";
        if (dumpDirectory != null) {
            File outputFile = new File(dumpDirectory, nameExpected);
            FileWriter outputWriter = null;
            try {
                outputWriter = new FileWriter(outputFile);
                outputWriter.append(actualText);
            }
            finally {
                if (outputWriter != null)
                    outputWriter.close();
            }
        }
        InputStream inputExpected =
                getClass().getResourceAsStream(nameExpected);
        if (inputExpected == null) {
            throw new Exception("YANG parser; missing expected result file: \""
                    + nameExpected + "\"");
        }
        String expectedText =
                StringUtils.readStringFromInputStream(inputExpected);
        assertEquals(expectedText, actualText);
    }

    @Test
    public void testModuleHeader() throws Exception {
        testYangFile("ModuleHeaderTest");
    }

    @Test
    public void testModuleImport() throws Exception {
        testYangFile("ModuleImportTest");
    }

    @Test
    public void testTypedef() throws Exception {
        testYangFile("TypedefTest");
    }

    @Test
    public void testData() throws Exception {
        testYangFile("DataTest");
    }

    @Test
    public void testComment() throws Exception {
        testYangFile("CommentTest");
    }
}
