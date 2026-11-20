package archfixtures.jsontree.verification;

import archfixtures.jsontree.wrapper.JsonBody;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypedVerification extends BaseVerification<TypedVerification> {

    public TypedVerification verifyItemIsInvalid(String body) throws Exception {
        ItemModel item = new ObjectMapper().readValue(body, ItemModel.class);
        if (item.isValid()) {
            throw new AssertionError("item is valid");
        }
        return self();
    }

    public TypedVerification verifyFirstItemIsInvalid(String text) {
        JsonBody body = new JsonBody(text);
        if (body.requiredAt("/items/0/valid").asBoolean()
                || body.readAt("/items/0/kind", String.class).isBlank()) {
            throw new AssertionError("first item is valid");
        }
        return self();
    }

    public static class ItemModel {

        private boolean valid;

        public boolean isValid() {
            return valid;
        }
    }
}
