package com.ponysdk.core.ui.form2.api;

public class ValidationResult {

    private static final ValidationResult OK_RESULT = new ValidationResult(true, null, null);
    private static final ValidationResult KO_RESULT = new ValidationResult(false, "Validation failed", null);

    private final boolean valid;
    private final String errorMessage;
    private final Object data;

    private ValidationResult(boolean valid, String errorMessage, Object data) {
        this.valid = valid;
        this.errorMessage = errorMessage;
        this.data = data;
    }

    public static ValidationResult OK() {
        return OK_RESULT;
    }

    public static ValidationResult OK(final Object data) {
        return new ValidationResult(true, null, data);
    }

    public static ValidationResult OK(final String message, final Object data) {
        return new ValidationResult(true, message, data);
    }

    public static ValidationResult KO() {
        return KO_RESULT;
    }

    public static ValidationResult KO(final String errorMessage) {
        return new ValidationResult(false, errorMessage, null);
    }

    public static ValidationResult KO(final String errorMessage, final Object data) {
        return new ValidationResult(false, errorMessage, data);
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isValid() {
        return valid;
    }

    public Object getData() {
        return data;
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "valid=" + valid +
                ", errorMessage='" + errorMessage + '\'' +
                ", data=" + data +
                '}';
    }
}
