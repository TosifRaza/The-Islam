package com.ikp.dev.app.core.supabase;

import com.ikp.dev.app.data.remote.dto.LibraryDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase PostgREST endpoints for reading and writing database tables.
 */
public interface SupabaseDatabaseApi {

    /**
     * Get active subjects/categories ordered by sort_order
     */
    @GET("rest/v1/subjects?select=*&status=eq.active&order=sort_order.asc")
    Call<List<LibraryDtos.SubjectDto>> getSubjects();

    /**
     * Get all Islamic events ordered by Hijri calendar month and day
     */
    @GET("rest/v1/islamic_events?select=*&order=hijri_month.asc,hijri_day.asc")
    Call<List<Map<String, Object>>> getIslamicEvents();

    /**
     * Get published courses from Supabase
     */
    @GET("rest/v1/academy_courses?select=*&status=eq.published")
    Call<List<com.ikp.dev.app.data.remote.dto.AcademyDtos.CourseRowDto>> getCourses();

    /**
     * Generic insert row into any table.
     * Use Prefer: return=representation to get the inserted object back.
     */
    @Headers("Prefer: return=representation")
    @POST("rest/v1/{table}")
    Call<List<Map<String, Object>>> insertRow(
            @retrofit2.http.Path("table") String tableName,
            @Body Map<String, Object> row
    );
}
