package archfixtures.jsontree.verification;

public abstract class BaseVerification<T extends BaseVerification<T>> {

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }
}
