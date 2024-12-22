package de.tum.cit.aet.util;

import java.util.ArrayList;
import java.util.List;

public class NumberRangeParser {

    /**
     * Regex for a string of the form "1-3,5,7-9".
     * Does not allow leading zeros.
     * All numbers must be positive.
     */
    public static final String numberRangeRegex = "\\b[1-9]\\d*(?:-[1-9]\\d*)?(?:,[1-9]\\d*(?:-[1-9]\\d*)?)*\\b";

    /**
     * Parses a string of the form "1-3,5,7-9" into a list of distinct integers.
     * Whitespaces are ignored.
     * All numbers must be positive with no leading zeros.
     * Multiple occurrences of the same number are allowed but will be ignored.
     *
     * @param rangeString the string to parse
     * @return the sorted list of integers
     */
    public static List<Integer> parseNumberRange(String rangeString) {
        rangeString = rangeString.replace(" ", "");
        if (!rangeString.matches(numberRangeRegex)) {
            throw new IllegalArgumentException("Invalid range string: " + rangeString);
        }

        List<Integer> result = new ArrayList<>();
        String[] ranges = rangeString.split(",");

        for (String range : ranges) {
            String[] numbers = range.split("-");
            if (numbers.length == 1) {
                result.add(Integer.parseInt(numbers[0]));
            } else if (numbers.length == 2) {
                int from = Integer.parseInt(numbers[0]);
                int to = Integer.parseInt(numbers[1]);
                if (from > to) {
                    throw new IllegalArgumentException("Invalid range string: " + rangeString);
                }
                for (int i = from; i <= to; i++) {
                    result.add(i);
                }
            } else {
                throw new IllegalArgumentException("Invalid range string: " + rangeString);
            }
        }
        return result.stream().distinct().sorted().toList();
    }
}
