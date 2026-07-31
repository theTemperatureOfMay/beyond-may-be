package com.example.beyond_may_be.apiPayload.exception.handler;

import com.example.beyond_may_be.apiPayload.code.BaseErrorCode;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;

public class UserHandler extends GeneralException {

  public UserHandler(BaseErrorCode code) {
    super(code);
  }
}
