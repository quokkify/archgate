package archfixtures.hashcode;

import java.util.Objects;

public class BothOverridden {

    private final String id;

    public BothOverridden(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BothOverridden that && Objects.equals(that.id, this.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
