package com.enmooy.deepseno.data.remote.api

import com.enmooy.deepseno.data.remote.model.*
import retrofit2.http.*

interface DeepSenoApi {
    @GET("api/ping")
    suspend fun ping(): PingResponse

    @GET("api/recordings")
    suspend fun getRecordings(): List<Recording>

    @GET("api/recordings/{id}/segments")
    suspend fun getSegments(@Path("id") id: Int): List<Segment>

    @GET("api/recordings/{id}/notes")
    suspend fun getMeetingNotes(@Path("id") id: Int): MeetingNotes

    @GET("api/recordings/{id}/images")
    suspend fun getImageInfo(@Path("id") id: Int): ImageInfo

    @GET("api/search")
    suspend fun search(@Query("q") query: String): List<SearchResult>

    @POST("api/query")
    suspend fun query(@Body body: QueryRequest): QueryResponse

    @GET("api/daily-summary/{date}")
    suspend fun getDailySummary(@Path("date") date: String): DailySummary

    @GET("api/weekly-summary/{startDate}")
    suspend fun getWeeklySummary(@Path("startDate") startDate: String): WeeklySummary

    // Monthly summary shares the weekly response shape (start_date/end_date/summary_json).
    @GET("api/monthly-summary/{startDate}")
    suspend fun getMonthlySummary(@Path("startDate") startDate: String): WeeklySummary

    @GET("api/extracted-items")
    suspend fun getExtractedItems(
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("recordingId") recordingId: Int? = null,
    ): List<ExtractedItem>

    @PATCH("api/extracted-items/{id}/status")
    suspend fun updateItemStatus(@Path("id") id: Int, @Body body: StatusUpdate)

    @GET("api/chat/sessions")
    suspend fun getChatSessions(): List<ChatSession>

    @POST("api/chat/sessions")
    suspend fun createChatSession(@Body body: CreateSessionRequest = CreateSessionRequest()): ChatSession

    @GET("api/chat/sessions/{id}/messages")
    suspend fun getSessionMessages(@Path("id") id: Int): List<ChatMessage>

    @POST("api/notes")
    suspend fun createNote(@Body body: NoteRequest)

    @GET("api/briefing")
    suspend fun getBriefing(@Query("date") date: String? = null): Briefing

    /**
     * Trigger server-side regeneration of the day's / week's briefing.
     * mode = "daily" | "weekly". Server can take 10-30s (LLM call); caller
     * should show a spinner. Response body is consumed but not used —
     * caller reloads via getDailySummary/getWeeklySummary.
     */
    @POST("api/briefing/regenerate")
    suspend fun regenerateBriefing(
        @Query("mode") mode: String,
        @Query("date") date: String,
    ): retrofit2.Response<okhttp3.ResponseBody>
}
