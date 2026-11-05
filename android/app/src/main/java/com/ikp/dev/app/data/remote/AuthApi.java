package com.ikp.dev.app.data.remote;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.dto.Dtos;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Authentication API — exact M1/M2 backend contract (docs/05 §3.1).
 * No new endpoints are invented; unimplemented capabilities (registration,
 * password reset) are absent here on purpose and handled as documented
 * unavailable states in the UI.
 */
public interface AuthApi {

    @POST("api/v1/auth/login")
    Call<ApiResponse<Dtos.TokenResponseDto>> login(@Body Dtos.LoginRequestDto request);

    @POST("api/v1/auth/refresh")
    Call<ApiResponse<Dtos.TokenResponseDto>> refresh(@Body Dtos.RefreshRequestDto request);

    @POST("api/v1/auth/logout")
    @Headers("Accept: application/json")
    Call<Void> logout(@Body Dtos.RefreshRequestDto request);

    @GET("api/v1/auth/me")
    Call<ApiResponse<Dtos.MeResponseDto>> me();

    @GET("api/v1/auth/sessions")
    Call<ApiResponse<List<Dtos.SessionDto>>> sessions();

    @DELETE("api/v1/auth/sessions/{sessionId}")
    Call<Void> revokeSession(@Path("sessionId") long sessionId);
}
