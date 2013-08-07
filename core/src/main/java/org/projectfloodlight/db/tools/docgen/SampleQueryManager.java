package org.projectfloodlight.db.tools.docgen;

import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.db.BigDBException;
import org.projectfloodlight.db.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.io.Files;

/**
 * A helper class to save/read sample queries from
 * unit tests and used for other places, e.g., documentation.
 *
 * @author kevin.wang@bigswitch.com
 *
 */

public class SampleQueryManager {

    protected final static ObjectMapper mapper;

    static {
        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        mapper = new ObjectMapper(jsonFactory);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
    }

    public static void addSampleQuery(String file, List<SampleQuery> sqs)
            throws BigDBException {
        Map<String, List<SampleQuery>> queries = readSampleQueries(file);
        if (queries == null) {
            queries = new LinkedHashMap<String, List<SampleQuery>>();
        }
        addSampleQuery(queries, sqs);
        saveSampleQueries(file, queries);
    }

    protected static void addSampleQuery(Map<String, List<SampleQuery>> queries,
                                         List<SampleQuery> lsqs) {
        for (SampleQuery sq : lsqs) {
            List<SampleQuery> sqs = queries.get(sq.getQueryContainerUri());
            if (sqs == null) {
                sqs = new ArrayList<SampleQuery>();
                queries.put(sq.getQueryContainerUri(), sqs);
                sqs.add(sq);
            } else {
                // check for existence
                boolean exist = false;
                for (SampleQuery q : sqs) {
                    if (q.getQueryUri().equals(sq.getQueryUri()) &&
                        q.getOperation().equals(sq.getOperation())) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) {
                    sqs.add(sq);
                }
            }
        }
    }

    public static void addSampleQuery(String file, String containerUri,
                                      String queryUri,
                                      String operation,
                                      String queryInput,
                                      String responseResource)
        throws BigDBException {
        Map<String, List<SampleQuery>> queries = readSampleQueries(file);
        if (queries == null) {
            queries = new LinkedHashMap<String, List<SampleQuery>>();
        }
        List<SampleQuery> sqs = new ArrayList<SampleQuery>();
        sqs.add(new SampleQuery(containerUri, queryUri, operation, queryInput,
                                responseResource));
        addSampleQuery(queries, sqs);
        // save back to file
        saveSampleQueries(file, queries);
    }

    public static void saveSampleQueries(String file, Map<String, List<SampleQuery>> queries)
            throws BigDBException {
        File outputFile = new File(file);
        try (BufferedWriter outputWriter = 
                Files.newWriter(outputFile, StandardCharsets.UTF_8)){
            mapper.writeValue(outputWriter, queries);
        } catch (Exception e) {
            throw new BigDBException("Failed to save sample queries.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<SampleQuery>> readSampleQueries(String resourceName)
            throws BigDBException {
        try {
            File f = new File(resourceName);
            if (!f.exists()) {
                //            throw new Exception("Confluence doc gen; " +
                //                    "missing resource file: \"" + resourceName + "\"");
                return new HashMap<String, List<SampleQuery>>();
            }
            return  mapper.readValue(f, HashMap.class);
        } catch (Exception e) {
            return new HashMap<String, List<SampleQuery>>();
        }
    }
    public static String readResponse(String resourceName)
            throws BigDBException {
        InputStream expectedInputStream =
                SampleQueryManager.class.getResourceAsStream(resourceName);
        if (expectedInputStream == null) {
            return null;
        }
        String expectedText = null;
        try {
            expectedText =
                StringUtils.readStringFromInputStream(expectedInputStream);
        } catch (Exception e) {
            throw new BigDBException("Failed to read response.", e);
        }
        return expectedText;
    }
}
