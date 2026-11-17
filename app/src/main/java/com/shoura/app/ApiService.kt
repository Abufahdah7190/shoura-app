package com.shoura.app

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @DELETE("api/account/me") fun deleteMyAccount(): Call<Void>
    @POST("api/auth/register") fun register(@Body request: RegisterRequest): Call<AuthResponse>
    @POST("api/auth/login") fun login(@Body request: LoginRequest): Call<AuthResponse>
    @GET("api/posts") fun getPosts(@Query("beforeId") beforeId: Int? = null, @Query("beforeCreatedAt") beforeCreatedAt: String? = null, @Query("limit") limit: Int = 20): Call<FeedPage>
    @POST("api/posts") fun createPost(@Body request: CreatePostRequest): Call<FeedPost>
    @Multipart @POST("api/media/upload") fun uploadMedia(@Part file: MultipartBody.Part): Call<MediaUpload>
    @GET("api/users") fun getUsers(): Call<List<PublicContact>>
    @GET("api/messages/{otherUserId}") fun getMessages(@Path("otherUserId") otherUserId: Int): Call<List<ChatMessage>>
    @POST("api/messages") fun sendMessage(@Body request: SendMessageRequest): Call<ChatMessage>
    @POST("api/ai/explain-video") fun explainVideo(@Body request: AiMediaRequest): Call<AiResult>
    @POST("api/ai/translate-audio") fun translateAudio(@Body request: AiMediaRequest): Call<AiResult>
}
