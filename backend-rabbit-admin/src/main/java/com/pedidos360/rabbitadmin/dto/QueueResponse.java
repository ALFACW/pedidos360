package com.pedidos360.rabbitadmin.dto;

public record QueueResponse(String name, int messageCount, int consumerCount) {
}
