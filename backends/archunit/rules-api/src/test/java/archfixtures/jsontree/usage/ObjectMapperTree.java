package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ObjectMapperTree {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String parse(String json) throws Exception {
        return MAPPER.readTree(json).toString();
    }

    public String convert(Object value) {
        return MAPPER.valueToTree(value).toString();
    }
}
