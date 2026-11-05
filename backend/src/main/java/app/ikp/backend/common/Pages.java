package app.ikp.backend.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Approved success envelope {"data": …, "meta": PageMeta} and paging helpers. */
public final class Pages {

    private Pages() {
    }

    public record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    public static <T, R> java.util.Map<String, Object> envelope(Page<T> page, Function<T, R> mapper) {
        List<R> content = page.getContent().stream().map(mapper).toList();
        return envelope(content, new PageMeta(page.getNumber() + 1, page.getSize(),
                page.getTotalElements(), page.getTotalPages()));
    }

    public static <T> java.util.Map<String, Object> envelope(List<T> data, PageMeta meta) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", meta);
        return body;
    }

    public static <T> java.util.Map<String, Object> envelope(T data) {
        return java.util.Map.of("data", data);
    }
}
