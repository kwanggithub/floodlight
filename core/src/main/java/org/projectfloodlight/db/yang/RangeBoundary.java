package org.projectfloodlight.db.yang;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=RangeBoundary.Serializer.class)
public class RangeBoundary {
    
    protected String stringValue;
    protected Long longValue;
    protected BigDecimal decimalValue;
    
    public static class Serializer extends JsonSerializer<RangeBoundary> {

        @Override
        public void serialize(RangeBoundary boundary, JsonGenerator jGen,
                SerializerProvider serializer)
                throws IOException, JsonProcessingException {
            if (boundary.getLongValue() != null) {
                jGen.writeNumber(boundary.getLongValue());
            } else if (boundary.getDecimalValue() != null) {
                jGen.writeNumber(boundary.getDecimalValue());
            } else {
                jGen.writeString((boundary.getStringValue() != null) ?
                        boundary.getStringValue() : "null");
            }
        }

    }

    public RangeBoundary() {
    }
    
    public RangeBoundary(String stringValue) {
        this.stringValue = stringValue;
    }
    
    public RangeBoundary(Long longValue, String stringValue) {
        this.longValue = longValue;
        this.stringValue = stringValue;
        
    }
    
    public RangeBoundary(BigDecimal decimalValue, String stringValue) {
        this.decimalValue = decimalValue;
        this.stringValue = stringValue;
    }
    
    public String getStringValue() {
        return stringValue;
    }
    
    public Long getLongValue() {
        return longValue;
    }
    
    public BigDecimal getDecimalValue() {
        return decimalValue;
    }
    
    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }
    
    public void setLongValue(Long longValue) {
        this.longValue = longValue;
    }
    
    public void setDecimalValue(BigDecimal decimalValue) {
        this.decimalValue = decimalValue;
    }
    
    public String toString() {
        return stringValue;
    }
}
