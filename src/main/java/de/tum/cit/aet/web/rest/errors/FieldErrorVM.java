package de.tum.cit.aet.web.rest.errors;

public record FieldErrorVM(String objectName, String field, String message) {
}
