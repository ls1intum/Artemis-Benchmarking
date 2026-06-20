package de.tum.cit.aet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards against MySQL -> PostgreSQL portability regressions in the Liquibase schema.
 *
 * <p>On PostgreSQL, Liquibase quotes mixed-case identifiers, so a column declared as e.g.
 * {@code onlineIde_percentage} is created case-sensitively and no longer matches Hibernate's
 * case-folded (lowercase) column name. This surfaces only at runtime as
 * "column ... does not exist". Keeping all column names lowercase avoids the whole class of bug.
 */
@IntegrationTest
class SchemaPortabilityIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allColumnNamesAreLowercase() {
        List<String> mixedCaseColumns = jdbcTemplate.queryForList(
            "SELECT table_name || '.' || column_name FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND column_name <> lower(column_name) ORDER BY 1",
            String.class
        );
        assertThat(mixedCaseColumns)
            .as(
                "mixed-case column names are not portable to PostgreSQL (Liquibase quotes them while " +
                    "Hibernate folds to lowercase); rename them to lowercase in the Liquibase changelog"
            )
            .isEmpty();
    }
}
