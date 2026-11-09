package com.ikp.dev.app.data.remote;

import com.ikp.dev.app.core.network.ApiResponse;
import com.ikp.dev.app.data.remote.dto.AssistantDtos;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface AssistantApi {

    @GET("api/v1/assistant/suggestions")
    Call<ApiResponse<List<AssistantDtos.SuggestionDto>>> suggestions();

    @POST("api/v1/assistant/query")
    Call<ApiResponse<AssistantDtos.AssistantResponseDto>> query(
            @Body AssistantDtos.AssistantQueryRequest req
    );

    @POST("api/v1/assistant/feedback")
    Call<ApiResponse<Map<String, Object>>> feedback(
            @Body AssistantDtos.AssistantFeedbackRequest req
    );
}
