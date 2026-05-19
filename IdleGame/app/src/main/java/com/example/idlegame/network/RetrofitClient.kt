package com.example.idlegame.network

import com.example.idlegame.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val BASE_URL = com.example.idlegame.BuildConfig.API_BASE_URL

    val apiService: ApiService by lazy {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            clientBuilder.addInterceptor(logging)
        }

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()
            .create(ApiService::class.java)
    }
}
