package com.wildme.wildbook_lite.dto;

public record UpdateObserverRequest(
    String name,
    String email,
    String organization
) {}
