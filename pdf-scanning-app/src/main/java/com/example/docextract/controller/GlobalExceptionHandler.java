package com.example.docextract.controller;

import com.example.docextract.exception.BadRequestException;
import com.example.docextract.exception.ExtractionFailedException;
import com.example.docextract.exception.ToolNotConfiguredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({BadRequestException.class, ToolNotConfiguredException.class, MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ResponseBody
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setTitle("Bad Request");
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }

    @ExceptionHandler({ExtractionFailedException.class, UnsupportedOperationException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ResponseBody
    public ProblemDetail handleProcessingFailure(Exception ex, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        detail.setTitle("Extraction Failed");
        detail.setProperty("path", request.getRequestURI());
        return detail;
    }
}
