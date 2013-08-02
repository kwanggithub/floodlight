package net.bigdb.query.parser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.bigdb.BigDBException;
import net.bigdb.ParserException;
import net.bigdb.expression.Expression;
import net.bigdb.expression.LocationPathExpression;
import net.bigdb.expression.UnionExpression;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.restlet.data.Reference;

/** static utility functions for parsing xpath expressions
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public final class XPathParserUtils {
    private XPathParserUtils() {}

    public static Expression parseExpression(String pathString, VariableReplacer variableReplacer)
            throws BigDBException {
        try {
            pathString = Reference.decode(pathString);
            InputStream is = 
                    new ByteArrayInputStream(pathString.
                                             getBytes(StandardCharsets.UTF_8));
            ANTLRInputStream input = new ANTLRInputStream(is);
            XPathLexer lexer = new XPathLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            XPathParser parser = new XPathParser(tokens);
            if(variableReplacer != null)
                parser.setVariableReplacer(variableReplacer);
            Expression expression = null;
            try {
                expression = parser.xPathExpr();
            } catch (StopParsingException e) {
                // ignore
            }
            if (parser.getErrors().size() > 0) {
                // Wouldn't we get a RecognitionException before we got here?
                throw new ParserException("Error parsing query base path: " +
                        pathString, parser.getErrors());
            }
            assert expression != null;
            return expression;
        }
        catch (RecognitionException e) {
            throw new BigDBException("Recognition error parsing query path: " + e, e);
        }
        catch (IOException e) {
            throw new BigDBException("I/O error parsing query path: " + e, e);
        }
    }

    public static LocationPathExpression parseSingleLocationPathExpression(String pathString, VariableReplacer variableReplacer) throws BigDBException {
        Expression expr = parseExpression(pathString, variableReplacer);
        if(!(expr instanceof LocationPathExpression)) {
            throw new BigDBException("Error parsing LocationPathExpression: parsed Expression is not a location path");
        }
        return (LocationPathExpression) expr;
    }

    public static Collection<? extends LocationPathExpression> parseAndExpandUnions(
            String path, VariableReplacer context) throws BigDBException {
        Expression selectedPathExpression = parseExpression(path, context);

        if (selectedPathExpression instanceof UnionExpression) {
            Set<LocationPathExpression> expressions =
                    new LinkedHashSet<LocationPathExpression>();
            UnionExpression unionExpression = (UnionExpression) selectedPathExpression;
            for (Expression expression : unionExpression.getExpressions()) {
                if (expression instanceof LocationPathExpression) {
                    expressions.add((LocationPathExpression) expression);
                } else {
                    throw new BigDBException("Select path must be an XPath "
                            + "union or location path: " + path);
                }
            }
            return expressions;
        } else if (selectedPathExpression instanceof LocationPathExpression) {
            return Collections.singleton((LocationPathExpression) selectedPathExpression);
        } else {
            throw new BigDBException("Select path must be an XPath "
                    + "union or location path: " + path);
        }
    }
}
