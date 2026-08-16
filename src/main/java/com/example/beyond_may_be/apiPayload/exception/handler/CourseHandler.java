package com.example.beyond_may_be.apiPayload.exception.handler;

import com.example.beyond_may_be.apiPayload.code.BaseErrorCode;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;

public class CourseHandler extends GeneralException {

  public CourseHandler(BaseErrorCode code) {
    super(code);
  }
}
