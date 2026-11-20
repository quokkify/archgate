package archfixtures.jsontree.usage;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Supplier;

public interface NodeSupplier extends Supplier<JsonNode> {}
