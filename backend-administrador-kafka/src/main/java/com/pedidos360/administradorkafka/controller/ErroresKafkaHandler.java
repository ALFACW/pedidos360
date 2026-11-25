package com.pedidos360.administradorkafka.controller;

import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.concurrent.ExecutionException;

/** Traduce los errores que devuelve Kafka a códigos HTTP claros. */
@RestControllerAdvice
public class ErroresKafkaHandler {

    @ExceptionHandler(ExecutionException.class)
    public ProblemDetail errorDeKafka(ExecutionException exception) {
        Throwable causa = exception.getCause();
        HttpStatus estado = causa instanceof TopicExistsException ? HttpStatus.CONFLICT
                : causa instanceof UnknownTopicOrPartitionException ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ProblemDetail.forStatusAndDetail(estado, causa.getMessage());
    }
}
