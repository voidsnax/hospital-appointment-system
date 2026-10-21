package com.hospital.app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(IllegalArgumentException e, Model model) {
        model.addAttribute("status", 404);
        model.addAttribute("error",
                (e.getMessage() != null && !e.getMessage().isBlank())
                        ? e.getMessage()
                        : "The requested resource was not found.");
        return "error";
    }
}