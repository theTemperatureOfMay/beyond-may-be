package com.example.beyond_may_be.common.websocket;

public class StompContractException extends RuntimeException {

  public enum Code {
    LOCATION_PAYLOAD_INVALID,
    LOCATION_ACCURACY_EXCEEDED,
    EXPLORATION_NOT_ONGOING,
    PARTICIPANT_NOT_ACTIVE,
    LOCATION_SHARING_DISABLED,
    LOCATION_PROCESSING_FAILED
  }

  private final Code code;

  public StompContractException(Code code) {
    super(code.name());
    this.code = code;
  }

  public StompContractException(Code code, Throwable cause) {
    super(code.name(), cause);
    this.code = code;
  }

  public Code getCode() {
    return code;
  }
}
