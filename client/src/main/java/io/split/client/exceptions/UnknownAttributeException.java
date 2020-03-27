package io.split.client.exceptions;

public class UnknownAttributeException extends RuntimeException {
    private final String _attribute;

    public UnknownAttributeException(String attribute) {
        _attribute = attribute;
    }

    public String attribute() {
        return _attribute;
    }
}
