package com.ikp.dev.app.core.supabase;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase GoTrue Authentication endpoints.
 */
public interface SupabaseAuthApi {

    @POST("auth/v1/signup")
    Call<SupabaseModels.AuthResponse> signUp(@Body SupabaseModels.AuthRequest request);

    @POST("auth/v1/token")
    Call<SupabaseModels.AuthResponse> signInWithPassword(
            @Query("grant_type") String grantType,
            @Body SupabaseModels.AuthRequest request
    );

    @POST("auth/v1/token")
    Call<SupabaseModels.AuthResponse> refreshToken(
            @Query("grant_type") String grantType,
            @Body Map<String, String> body
    );

    @POST("auth/v1/logout")
    Call<Void> signOut();

    @GET("auth/v1/user")
    Call<SupabaseModels.User> getUser();
}
