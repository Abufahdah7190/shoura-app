package com.shoura.app

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    val baseUrl: String = BuildConfig.API_BASE_URL
    @Volatile private var service: ApiService? = null

    fun api(context: Context): ApiService {
        service?.let { return it }
        return synchronized(this) {
            service ?: run {
                val prefs = context.applicationContext.getSharedPreferences("shoura", Context.MODE_PRIVATE)
                val client = OkHttpClient.Builder().addInterceptor { chain ->
                    val token = prefs.getString("access_token", null)
                    val request = chain.request().newBuilder().apply {
                        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                    }.build()
                    chain.proceed(request)
                }.build()
                Retrofit.Builder().baseUrl(baseUrl).client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build()
                    .create(ApiService::class.java).also { service = it }
            }
        }
    }
}
