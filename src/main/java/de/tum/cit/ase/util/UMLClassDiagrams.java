package de.tum.cit.ase.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.io.IOUtils;

public final class UMLClassDiagrams {

    public static final String CLASS_MODEL_1;

    public static final String CLASS_MODEL_2;

    static {
        try {
            CLASS_MODEL_1 =
                IOUtils.toString(
                    Objects.requireNonNull(UMLClassDiagrams.class.getClassLoader().getResource("classModel1.json")),
                    StandardCharsets.UTF_8
                );
            CLASS_MODEL_2 =
                IOUtils.toString(
                    Objects.requireNonNull(UMLClassDiagrams.class.getClassLoader().getResource("classModel2.json")),
                    StandardCharsets.UTF_8
                );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private UMLClassDiagrams() {
        // do not instantiate
    }
}
