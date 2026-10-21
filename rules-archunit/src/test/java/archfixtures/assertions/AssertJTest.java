package archfixtures.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import org.testng.annotations.Test;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class AssertJTest {

    @Test
    public void checksName() {
        assertThat("archgate").startsWith("arch");
    }
}
