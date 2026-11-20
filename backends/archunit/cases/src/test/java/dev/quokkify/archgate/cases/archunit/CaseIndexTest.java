package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;

public class CaseIndexTest {

    @Test(description = "An id may sit in one category only")
    public void rejectsAnIdInTwoCategories() {
        assertThatThrownBy(() -> new CaseIndex(Map.of(1, AutomationType.notSet()), Set.of(1), Set.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1");
        assertThatThrownBy(() -> new CaseIndex(Map.of(), Set.of(2), Set.of(2), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseIndex(Map.of(), Set.of(), Set.of(3), Map.of(3, "P7")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test(description = "Ids must be positive and project labels must not be blank")
    public void rejectsBadIdsAndLabels() {
        assertThatThrownBy(() -> new CaseIndex(Map.of(), Set.of(0), Set.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CaseIndex(Map.of(), Set.of(), Set.of(), Map.of(4, " ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test(description = "Null collections and null entries are rejected")
    public void rejectsNulls() {
        Map<Integer, AutomationType> nullType = new HashMap<>();
        nullType.put(1, null);
        Set<Integer> nullId = new HashSet<>();
        nullId.add(null);

        assertThatThrownBy(() -> new CaseIndex(null, Set.of(), Set.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CaseIndex(nullType, Set.of(), Set.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CaseIndex(Map.of(), nullId, Set.of(), Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test(description = "The index copies its inputs, so later changes to them do not leak in")
    public void copiesInputs() {
        Set<Integer> missing = new HashSet<>(Set.of(5));
        CaseIndex index = new CaseIndex(Map.of(), missing, Set.of(), Map.of());

        missing.add(6);

        assertThat(index.missing()).containsExactly(5);
        assertThatThrownBy(() -> index.missing().add(7)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test(description = "Every id is found in exactly one category, and an unknown id in none")
    public void classifiesIds() {
        CaseIndex index =
                new CaseIndex(Map.of(1, AutomationType.named("Automated")), Set.of(2), Set.of(3), Map.of(4, "P7"));

        assertThat(index.classifies(1)).isTrue();
        assertThat(index.classifies(2)).isTrue();
        assertThat(index.classifies(3)).isTrue();
        assertThat(index.classifies(4)).isTrue();
        assertThat(index.classifies(5)).isFalse();
        assertThat(CaseIndex.empty().classifies(1)).isFalse();
    }
}
