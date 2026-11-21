package archfixtures.assertions;

public class ComposedEmptyTest {

    @UnitCase
    void runsCodeButChecksNothing() {
        String.valueOf(1);
    }
}
