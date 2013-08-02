package net.bigdb.yang;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=LengthBoundary.Serializer.class)
public class LengthBoundary {
    
    protected String stringValue;
    protected Long longValue;

    public static class Serializer extends JsonSerializer<LengthBoundary> {

        @Override
        public void serialize(LengthBoundary boundary, JsonGenerator jGen,
                SerializerProvider serializer)
                throws IOException, JsonProcessingException {
            if (boundary.getLongValue() != null) {
                jGen.writeNumber(boundary.getLongValue());
            } else {
                jGen.writeString((boundary.getStringValue() != null) ?
                        boundary.getStringValue() : "null");
            }
        }
    }

    public LengthBoundary() {
    }
    
    public LengthBoundary(String stringValue) {
        this.stringValue = stringValue;
    }
    
    public LengthBoundary(Long longValue, String stringValue) {
        this.longValue = longValue;
        this.stringValue = stringValue;
    }
    
    public String getStringValue() {
        return stringValue;
    }
    
    public Long getLongValue() {
        return longValue;
    }
    
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
    
    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }
    
    public String toString() {
        return stringValue;
    }
}
