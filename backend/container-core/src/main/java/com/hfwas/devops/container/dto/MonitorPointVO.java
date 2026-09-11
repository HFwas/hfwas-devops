package com.hfwas.devops.container.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.IOException;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonitorPointVO {
    /** Unix epoch seconds. Global Long→String serializer would break ECharts time axis. */
    @JsonSerialize(using = EpochSecondsSerializer.class)
    private long timestamp;
    private double value;

    public static class EpochSecondsSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeNumber(value.longValue());
            }
        }
    }
}