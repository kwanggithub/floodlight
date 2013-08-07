package org.projectfloodlight.db.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.junit.Test;
import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.expression.BinaryOperatorExpression;
import org.projectfloodlight.db.expression.Expression;
import org.projectfloodlight.db.expression.IntegerLiteralExpression;
import org.projectfloodlight.db.expression.LocationPathExpression;
import org.projectfloodlight.db.expression.StringLiteralExpression;
import org.projectfloodlight.db.query.Query;
import org.projectfloodlight.db.query.Step;
import org.projectfloodlight.db.query.Query.StateType;
import org.projectfloodlight.db.query.parser.VariableNotFoundException;

import com.google.common.collect.ImmutableList;

public class QueryTest {
    @Test
    public void testCreateBasic() throws BigDBException {
        Query q = Query.parse("/person");
        assertEquals(q.getBasePath().toString(), "/person");
    }

    @Test
    public void testCreateRootQuery() throws BigDBException {
        Query q = Query.parse("/");
        assertEquals("/", q.getBasePath().toString());
        assertTrue(q.getBasePath().isAbsolute());
    }

    @Test(expected=BigDBException.class)
    public void testCreateEmptyQueryFails() throws BigDBException {
        Query.parse("");
    }

    @Test(expected=VariableNotFoundException.class)
    public void testCreateQueryWithNonExistantVar() throws BigDBException {
        Query.builder().setBasePath("/people[name = $notfound]").getQuery();
    }
    @Test
    public void testCreateQueryWithTwoVars() throws BigDBException {
        Query q = Query.builder().setBasePath("/people[name = $name]/phones[type = $phoneType]").setVariable("name", "foobar").setVariable("phoneType", 1).getQuery();
        LocationPathExpression basePath = q.getBasePath();

        Step step = basePath.getStep(0);
        assertEquals("people", step.getName());
        assertEquals(1, step.getPredicates().size());
        Expression expression = step.getPredicates().get(0);
        assertTrue(expression instanceof BinaryOperatorExpression);
        Expression rightLiteral = ((BinaryOperatorExpression) expression).getRightExpression();
        assertTrue(rightLiteral instanceof StringLiteralExpression);
        assertEquals("foobar",  ((StringLiteralExpression) rightLiteral).getValue());

        Step step2 = basePath.getStep(1);
        assertEquals("phones", step2.getName());
        assertEquals(1, step.getPredicates().size());
        Expression expression2 = step2.getPredicates().get(0);
        assertTrue(expression2 instanceof BinaryOperatorExpression);
        Expression rightLiteral2 = ((BinaryOperatorExpression) expression2).getRightExpression();
        assertTrue("right side literatal should be IntegerLiteralExpression but is "+rightLiteral2.getClass(), rightLiteral2 instanceof IntegerLiteralExpression);
        assertEquals(Long.valueOf(1),  ((IntegerLiteralExpression) rightLiteral2).getValue());
    }

    @Test
    public void testCreateQueryWithVar() throws BigDBException {
        // http://xkcd.com/327/
        Query q = Query.builder().setBasePath("/people[name = $name]").setVariable("name", "little bobby\"]; drop tables students;").getQuery();
        LocationPathExpression basePath = q.getBasePath();
        assertTrue(basePath.isAbsolute());
        Step step = basePath.getStep(0);
        assertEquals("people", step.getName());
        assertEquals(1, step.getPredicates().size());
        Expression expression = step.getPredicates().get(0);
        assertTrue(expression instanceof BinaryOperatorExpression);
        Expression rightLiteral = ((BinaryOperatorExpression) expression).getRightExpression();
        assertTrue(rightLiteral instanceof StringLiteralExpression);
        assertEquals("little bobby\"]; drop tables students;",  ((StringLiteralExpression) rightLiteral).getValue());
    }

    @Test
    public void testToUri() throws BigDBException, UnsupportedEncodingException {
        for (String a : ImmutableList.of("/switch/port", "switch/port",
                "/switch[dpid=\"00:01:02:03:04:05:06\"]/port[mac=\"01:02:03:04:05:06\"]",
                "/switch[id>1]")) {

            Query query = Query.parse(a);
            String expected = URLEncoder.encode(a, "UTF-8").replace("%2F", "/").replace("%3D", "=")
                    .replace("%3A", ":").replace("+", "%20");

            assertEquals(expected, query.toURI().toString());


            Query query2 = new Query.Builder().setBasePath("/switch[query-type=\"abc\"]").setIncludedStateType(StateType.CONFIG).addSelectPath("port").getQuery();
            assertEquals("/switch%5Bquery-type=%22abc%22%5D?select=port&config=true", query2.toURI().toString());
        }
    }

}
