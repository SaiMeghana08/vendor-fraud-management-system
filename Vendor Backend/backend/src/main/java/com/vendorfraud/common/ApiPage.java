package com.vendorfraud.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** Transport-friendly page wrapper so Spring's Page shape is not leaked. */
public record ApiPage<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> ApiPage<T> from(Page<T> page) {
        return new ApiPage<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
