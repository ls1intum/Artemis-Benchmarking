package de.tum.cit.ase.service.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record AuthToken(String jwtToken, String path, Long maxAge, ZonedDateTime expireDate) {
    // Example: 20 Sep 2023 23:10:09 GMT
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd LLL yyyy HH:mm:ss zzz", Locale.ENGLISH);

    public static AuthToken fromResponseHeaderString(String authTokenAsString) {
        var components = authTokenAsString.split(";");
        var token = components[0]; // TODO: we could leave out "jwt=" here in the future and build this on our own
        var path = components[1];
        var maxAge = Long.valueOf(components[2].split("=")[1]);

        var dateString = components[3].split("=")[1];
        var expireDate = ZonedDateTime.parse(dateString, formatter);
        return new AuthToken(token, path, maxAge, expireDate);
    }

    @Override
    public String toString() {
        return jwtToken + ";" + path + "; Max-Age=" + maxAge + "; Expires=" + expireDate.format(formatter);
    }
}
