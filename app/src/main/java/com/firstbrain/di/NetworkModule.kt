package com.firstbrain.di

import com.firstbrain.data.auth.AuthConstants
import com.firstbrain.data.auth.AuthInterceptor
import com.firstbrain.data.auth.NeonAuthApi
import com.firstbrain.data.remote.NeonTasksApi
import com.firstbrain.data.remote.RecommendationApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthClient
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApiClient

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class NeonDataRetrofit
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class RecommendationRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    /** Unauthenticated client used to talk to Better Auth itself. */
    @Provides
    @Singleton
    @AuthClient
    fun provideAuthClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

    /** Authenticated client used for Neon Data API + the ML recommendation server. */
    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(
        logging: HttpLoggingInterceptor,
        auth: AuthInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()

    @Provides
    @Singleton
    @AuthRetrofit
    fun provideAuthRetrofit(@AuthClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AuthConstants.NEON_AUTH_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @NeonDataRetrofit
    fun provideNeonDataRetrofit(@ApiClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AuthConstants.NEON_DATA_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    @RecommendationRetrofit
    fun provideRecommendationRetrofit(@ApiClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(AuthConstants.RECOMMENDATION_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideNeonAuthApi(@AuthRetrofit retrofit: Retrofit): NeonAuthApi =
        retrofit.create(NeonAuthApi::class.java)

    @Provides
    @Singleton
    fun provideNeonTasksApi(@NeonDataRetrofit retrofit: Retrofit): NeonTasksApi =
        retrofit.create(NeonTasksApi::class.java)

    @Provides
    @Singleton
    fun provideRecommendationApi(@RecommendationRetrofit retrofit: Retrofit): RecommendationApi =
        retrofit.create(RecommendationApi::class.java)
}
