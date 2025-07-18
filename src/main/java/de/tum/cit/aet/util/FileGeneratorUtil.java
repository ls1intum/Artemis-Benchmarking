package de.tum.cit.aet.util;

import org.springframework.core.io.ByteArrayResource;

import java.util.Arrays;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileGeneratorUtil {

    private static final Map<Integer, byte[]> contentCache = new ConcurrentHashMap<>();

    /**
     * Returns a cached dummy file of a given size filled with 'A'.
     * If not cached yet, generates and stores it.
     *
     * @param sizeInBytes File size in bytes.
     * @param filename    File name.
     * @return ByteArrayResource representing the file.
     */
    public static ByteArrayResource getDummyFile(int sizeInBytes, String filename) {
        byte[] content = contentCache.computeIfAbsent(sizeInBytes, size -> {
            byte[] data = new byte[size];
            Arrays.fill(data, (byte) 'A');
            return data;
        });

        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
