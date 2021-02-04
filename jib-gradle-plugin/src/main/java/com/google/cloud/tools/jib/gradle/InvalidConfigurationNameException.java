package com.google.cloud.tools.jib.gradle;

public class InvalidConfigurationNameException extends Exception {

    private final String invalidConfigurationName;

    public InvalidConfigurationNameException(String message, String invalidConfigurationName) {
        super(message);
        this.invalidConfigurationName = invalidConfigurationName;
    }

    public String getInvalidConfigurationName() {
        return invalidConfigurationName;
    }
}
