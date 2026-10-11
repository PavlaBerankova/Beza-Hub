package com.berpod.app.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * How the app introduces itself. Some hosts (Buzzsprout, for one) answer the default "okhttp/x.y" with 403 for
 * covers and audio alike, so every request the app makes says who it really is.
 */
const val HTTP_USER_AGENT = "BEZA Hub (Android RSS reader)"

/** An OkHttp client that sends [HTTP_USER_AGENT] with every request, for covers (Coil) and episode downloads. */
fun appHttpClient(readTimeoutSeconds: Long = 30): OkHttpClient =
    OkHttpClient.Builder()
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", HTTP_USER_AGENT).build())
        })
        .build()
