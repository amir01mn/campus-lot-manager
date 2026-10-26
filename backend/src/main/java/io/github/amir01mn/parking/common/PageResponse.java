package io.github.amir01mn.parking.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paginated lists: {@code {content, totalElements, totalPages, number, size}}. */
public record PageResponse<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<? super E, ? extends T> mapper) {
        List<T> content = page.getContent().stream().<T>map(mapper).toList();
        return new PageResponse<>(content, page.getTotalElements(), page.getTotalPages(), page.getNumber(),
                page.getSize());
    }
}
