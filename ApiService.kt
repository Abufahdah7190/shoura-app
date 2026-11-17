package com.example.shoura_app

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @GET("api/posts")
    fun getPosts(): Call<List<Post>>

    @POST("api/posts")
    fun createPost(@Body post: Post): Call<Post>
}