package com.interswitch.walletapp.exceptions;

public class FraudDetectedException extends RuntimeException {

    public FraudDetectedException() {
        super();
    }

    public FraudDetectedException(String message) {
        super(message);
    }

    public FraudDetectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public FraudDetectedException(Throwable cause) {
        super(cause);
    }
}
