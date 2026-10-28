package archfixtures.hashcode;

public class OnlyEquals {

    private final String id;

    public OnlyEquals(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OnlyEquals that && that.id.equals(this.id);
    }
}
