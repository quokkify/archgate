package archfixtures.hashcode;

public class OnlyHashCode {

    private final String id;

    public OnlyHashCode(String id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
