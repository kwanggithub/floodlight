package net.bigdb.query;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.*;

import net.bigdb.BigDBException;
import net.bigdb.expression.BinaryOperatorExpression;
import net.bigdb.expression.Expression;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.query.Step.ExactMatchPredicate;
import net.bigdb.query.Step.PrefixMatchPredicate;
import net.bigdb.query.parser.XPathLexer;
import net.bigdb.query.parser.XPathParser;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.junit.Test;
import org.restlet.data.Reference;

public class XPathParserTest {
    static String encoded = "http://localhost:8082/api/v1/data/controller/core/" +
                            "controller/counter%5Bcount-name%3D%22OFPacketIn%22%5D" +
                            "/sub-category%5Bname%3D%22L2%22%5D";
    static String xPath = "/core/controller/counter[count-name=\"OFPacketIn\"]/sub-category[name=\"L2\"]";


    @Test
    public void testUriDecoder() {

        Reference ref = new Reference(encoded);
        ref.setBaseRef("http://localhost:8082/api/v1/data/controller");
        String remainingPart = ref.getRemainingPart(true);

        assertEquals(xPath, remainingPart);
    }

    @Test
    public void baseParserTest() throws IOException {
        InputStream is = new ByteArrayInputStream(xPath.getBytes());
        ANTLRInputStream input = new ANTLRInputStream(is);
        XPathLexer lexer = new XPathLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XPathParser parser = new XPathParser(tokens);
        try {
            Expression expr = parser.xPathExpr();
            if (parser.getErrors().size() > 0) {
                throw new BigDBException(parser.getErrors().toString());
            }
            assertNotNull(expr);
        } catch (Exception e) {

            assertTrue("Failed to parse xpath: " + xPath +
                              "; " + e.getMessage(), false);
        }
    }

    @Test
    public void relativePathTest() throws IOException {
        String relativePath = "step1/step2/step3";
        InputStream is = new ByteArrayInputStream(relativePath.getBytes());
        ANTLRInputStream input = new ANTLRInputStream(is);
        XPathLexer lexer = new XPathLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XPathParser parser = new XPathParser(tokens);
        try {
            Expression expr = parser.xPathExpr();
            if (parser.getErrors().size() > 0) {
                throw new BigDBException(parser.getErrors().toString());
            }
            LocationPathExpression le = (LocationPathExpression)expr;
            assertTrue(le.getSteps().size() == 3);
            int i = 0;
            for (Step s : le.getSteps()) {
                assertEquals(s.getName(), "step" + Integer.toString(++i));
            }
        } catch (Exception e) {

            assertTrue("Failed to parse xpath: " + xPath +
                              "; " + e.getMessage(), false);
        }
    }

    private void relativePathWithPredicates(String uri, String op, String name,
            String expectedValue) throws IOException {

        InputStream is = new ByteArrayInputStream(uri.getBytes());
        ANTLRInputStream input = new ANTLRInputStream(is);
        XPathLexer lexer = new XPathLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        XPathParser parser = new XPathParser(tokens);
        try {
            Expression expr = parser.xPathExpr();
            if (parser.getErrors().size() > 0) {
                throw new BigDBException(parser.getErrors().toString());
            }
            LocationPathExpression le = (LocationPathExpression)expr;
            assertNotNull(le);
            assertTrue(le.getSteps().size() == 6);
            Step step = le.getSteps().get(5);
            Object value = null;
            if (op.equals("starts-with")) {
                value = step.getPrefixMatchPredicateString(name);
            } else if (op.equals("EQ")) {
                value = step.getExactMatchPredicateValue(name);
            } else {
                fail();
            }
            assertEquals(value, expectedValue);
            //assertVariableInExpression(step, op, name, value);
        } catch (Exception e) {

            assertTrue("Failed to parse xpath: " + xPath +
                              "; " + e.getMessage(), false);
        }
    }

    @Test
    public void relativePathStartsWithAxis() throws IOException {
        String relativePath = "api/v1/data/controller/core/device" +
                "[starts-with(child::attachment-point/ip-address,\"10.11\")]";
        this.relativePathWithPredicates(relativePath, "starts-with",
                                        "attachment-point/ip-address", "10.11");
    }

    @Test
    public void relativePathStartsWithoutAxis() throws IOException {
        String relativePath = "api/v1/data/controller/core/device" +
                "[starts-with(attachment-point/ip-address,\"10.11\")]";
        this.relativePathWithPredicates(relativePath, "starts-with",
                                        "attachment-point/ip-address", "10.11");
    }

    @Test
    public void relativePathEqualPredicate() throws IOException {
        String relativePath = "api/v1/data/controller/core/device" +
                "[attachment-point/ip-address=\"10.11.0.1\"]";
        this.relativePathWithPredicates(relativePath,
                                        BinaryOperatorExpression.Operator.EQ.toString(),
                                        "attachment-point/ip-address", "10.11.0.1");
    }

    @Test
    public void testOneExactMatchPredicate() throws Exception {
        Query query = Query.parse("foo[dummy=\"Hello\"]");
        Step step = query.getSteps().get(0);
        Object value = step.getExactMatchPredicateValue("dummy");
        assertTrue("Hello".equals(value));
        value = step.getExactMatchPredicateValue("foobar");
        assertNull(value);
    }

    @Test
    public void testAllExactMatchPredicates() throws Exception {
        Query query = Query.parse("foo[dummy=\"Hello\"][test/dummy > 5][foo=\"Bar\"][dir/foo/bar = 9]");
        Step step = query.getSteps().get(0);
        List<ExactMatchPredicate> exactMatchPredicates = step.getExactMatchPredicates();
        assertEquals(exactMatchPredicates.size(), 3);
        assertEquals(exactMatchPredicates.get(0).getValue(), "Hello");
        assertEquals(exactMatchPredicates.get(1).getValue(), "Bar");
        assertEquals(exactMatchPredicates.get(2).getValue(), 9L);
    }

    @Test
    public void testExactMatchStringPredicate() throws Exception {
        Object[] testValues = { "bar", 23453, -1, -3453, 1000.65, -333.33 };

        for (Object testValue: testValues) {
            String queryValue = testValue.toString();
            if (testValue instanceof String)
                queryValue = "\"" + testValue + "\"";
            Query query = Query.parse(String.format("foo[test=%s]", queryValue));
            Step step = query.getBasePath().getStep(0);
            String value = step.getExactMatchPredicateString("test");
            assertEquals(value, testValue.toString());
        }
    }

    @Test
    public void testOnePrefixMatchPredicate() throws Exception {
        Query query = Query.parse("foo[starts-with(foo/bar,\"Hello\")]");
        Step step = query.getSteps().get(0);
        Object value = step.getPrefixMatchPredicateString("foo/bar");
        assertTrue("Hello".equals(value));
        value = step.getPrefixMatchPredicateString("foobar");
        assertNull(value);
    }

    @Test
    public void testAllPrefixMatchPredicates() throws Exception {
        Query query = Query.parse("foo[starts-with(dummy,\"Hello\")][foo=\"Bar\"][starts-with(dir/foo/bar,\"Test\")]");
        Step step = query.getSteps().get(0);
        List<PrefixMatchPredicate> prefixMatchPredicates = step.getPrefixMatchPredicates();
        assertEquals(prefixMatchPredicates.size(), 2);
        assertEquals(prefixMatchPredicates.get(0).getPrefix(), "Hello");
        assertEquals(prefixMatchPredicates.get(1).getPrefix(), "Test");
    }
}
