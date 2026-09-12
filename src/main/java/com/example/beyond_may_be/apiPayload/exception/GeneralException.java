package com.example.beyond_may_be.apiPayload.exception;

import com.example.beyond_may_be.apiPayload.code.BaseErrorCode;
import com.example.beyond_may_be.apiPayload.code.ErrorReasonDTO;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

  private final BaseErrorCode code;
  private final Object data;

  public GeneralException(BaseErrorCode code) {
    this(code, null);
  }

  public GeneralException(BaseErrorCode code, Object data) {
    super(code.getReason().getMessage());
    this.code = code;
    this.data = data;
  }

  public ErrorReasonDTO getErrorReason() {
    return this.code.getReason();
  }

  public ErrorReasonDTO getErrorReasonHttpStatus() {
    return this.code.getReasonHttpStatus();
  }
}
