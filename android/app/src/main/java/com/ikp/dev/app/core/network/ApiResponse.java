package com.ikp.dev.app.core.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Backend success envelope: {@code {"data": …, "meta": …}} (docs/05 §1.2).
 * {@code meta} is present on paged/list responses; absent on single-object
 * envelopes ({@code Pages.envelope(data)} serialises data only when meta is
 * null — Gson is configured to skip nulls).
 */
public final class ApiResponse<T> {

    public final T data;
    @SerializedName("meta")
    public final Meta meta;

    public ApiResponse(T data, Meta meta) {
        this.data = data;
        this.meta = meta;
    }

    public boolean hasMeta() {
        return meta != null;
    }

    /** Paging metadata (1-based page numbering server-side). */
    public static final class Meta {
        public Integer page;
        public Integer size;
        public Long totalElements;
        public Integer totalPages;
        // Library books endpoint uses cursor paging instead:
        public Integer limit;
        @SerializedName(value = "nextCursor", alternate = "next_cursor")
        public String nextCursor;
    }

    /** Helper for list payloads. */
    public static <E> List<E> listOrEmpty(List<E> list) {
        return list == null ? java.util.Collections.<E>emptyList() : list;
    }
}
