package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.TreeSet;
import org.testng.annotations.Test;

public class CaseQueryTest {

    @Test(description = "The ids are kept sorted and cannot be changed by the service")
    public void idsAreSortedAndUnmodifiable() {
        TreeSet<Integer> ids = new TreeSet<>(Set.of(30, 4, 12));
        CaseQuery query = new CaseQuery(ids, true);

        ids.add(99);

        assertThat(query.ids()).containsExactly(4, 12, 30);
        assertThat(query.changedFilesOnly()).isTrue();
        assertThatThrownBy(() -> query.ids().add(1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test(description = "A query asks for at least one positive id")
    public void rejectsEmptyAndNonPositiveIds() {
        assertThatThrownBy(() -> new CaseQuery(new TreeSet<>(), false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseQuery(new TreeSet<>(Set.of(0)), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseQuery(null, false)).isInstanceOf(NullPointerException.class);
    }
}
