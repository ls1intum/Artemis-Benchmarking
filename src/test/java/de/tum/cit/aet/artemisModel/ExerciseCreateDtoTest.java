package de.tum.cit.aet.artemisModel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The exercise group has to reach Artemis whichever way that version of Artemis reads it.
 * <p>
 * Artemis 9 bound the full exercise entity and took the nested {@code exerciseGroup}; Artemis 10 binds a DTO with a
 * flat {@code exerciseGroupId} and ignores the nested object. Sending only the nested form against Artemis 10 left the
 * group unset server-side, and a 500-user run against staging2 died on "An exercise must have either a course or an
 * exercise group" before a single student had logged in.
 */
class ExerciseCreateDtoTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void aModelingExerciseCarriesTheGroupInBothShapes() throws Exception {
        var json = MAPPER.readTree(MAPPER.writeValueAsString(ModelingExerciseCreateDTO.forBenchmarking("Modeling Exercise", 42L)));

        assertThat(json.path("exerciseGroup").path("id").asLong()).as("the shape Artemis 9 reads").isEqualTo(42L);
        assertThat(json.path("exerciseGroupId").asLong()).as("the shape Artemis 10 reads").isEqualTo(42L);
    }

    @Test
    void aTextExerciseCarriesTheGroupInBothShapes() throws Exception {
        var json = MAPPER.readTree(MAPPER.writeValueAsString(TextExerciseCreateDTO.forBenchmarking("Text Exercise", 7L)));

        assertThat(json.path("exerciseGroupId").asLong()).isEqualTo(7L);
    }

    @Test
    void theGroupIsNeverSerialisedAsNull() throws Exception {
        // A null here is worse than a missing field: it can overwrite a value Artemis would otherwise infer.
        var json = MAPPER.readTree(MAPPER.writeValueAsString(ModelingExerciseCreateDTO.forBenchmarking("Modeling Exercise", 1L)));

        assertThat(json.path("exerciseGroupId").isNull()).isFalse();
        assertThat(json.path("exerciseGroup").isNull()).isFalse();
    }
}
