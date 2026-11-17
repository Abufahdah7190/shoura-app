package com.example.shoura_app

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    // المنشورات
    @GET("api/posts")
    fun getPosts(): Call<List<Post>>

    @POST("api/posts")
    fun createPost(@Body post: Post): Call<Post>

    // الحسابات
    @POST("api/auth/register")
    fun register(@Body user: User): Call<User>

    @POST("api/auth/login")
    fun login(@Body loginDto: LoginDto): Call<User>

    @PUT("api/auth/update/{id}")
    fun updateProfile(@Path("id") userId: Int, @Body updateData: UpdateUserDto): Call<ResponseBody>

    @DELETE("api/auth/delete/{id}")
    fun deleteAccount(@Path("id") userId: Int): Call<ResponseBody>
}