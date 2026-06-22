package com.wildme.wildbook_lite.bulkimport.dto;


public record ValidationError(
    Integer rowIndex,
    String fieldName,
    String code,
    String message
) {
}