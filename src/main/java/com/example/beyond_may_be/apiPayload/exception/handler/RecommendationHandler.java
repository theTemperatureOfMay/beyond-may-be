package com.example.beyond_may_be.apiPayload.exception.handler;

import com.example.beyond_may_be.apiPayload.code.BaseErrorCode;
import com.example.beyond_may_be.apiPayload.exception.GeneralException;

public class RecommendationHandler extends GeneralException {

  public RecommendationHandler(BaseErrorCode code) {
    super(code);
  }
}
