package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.function.BiFunction;

public class JsonMapperAndMethodReference {

    public String parse(String json) throws Exception {
        return JsonMapper.builder().build().readTree(json).toString();
    }

    public BiFunction<String, String, ?> putter(ObjectNode target) {
        return target::put;
    }
}
