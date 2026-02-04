package io.github.vdaburon.jmeter.har;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.jackson.MapperFactory;

public class MyMapperFactory implements MapperFactory {

    int maxStringLength = HarForJMeter.K_JACKSON_PARSER_STRING_MAX_DEFAULT;

    public ObjectMapper instance(HarReaderMode mode) {
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory jsonFactory = mapper.getJsonFactory();
        StreamReadConstraints streamReadConstraints = StreamReadConstraints.builder()
                .maxStringLength(getMaxStringLength()).build();
        jsonFactory.setStreamReadConstraints(streamReadConstraints);
        SimpleModule module = new SimpleModule();
        mapper.registerModule(module);
        return mapper;
    }

    public ObjectMapper instance() {
        return new ObjectMapper();
    }

    public int getMaxStringLength() {
        return maxStringLength;
    }

    public void setMaxStringLength(int maxStringLength) {
        this.maxStringLength = maxStringLength;
    }
}
