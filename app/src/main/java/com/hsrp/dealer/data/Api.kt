package com.hsrp.dealer.data

import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// ------------------------------------------------------------------------------------------- models

data class LoginRequest(val username: String, val password: String, val deviceId: String, val deviceName: String)
data class UserDto(val id: String, val username: String, val displayName: String, val roles: List<String>)
data class LoginResponse(val token: String, val user: UserDto)

data class DealerDto(val id: String, val code: String, val name: String, val city: String, val stateCode: String)
data class Counts(val toInward: Int, val toFit: Int, val awaitingVahan: Int, val inProduction: Int, val rejectionReview: Int, val completed: Int)
data class Appointment(val slotDate: String, val slotStart: String, val orderNo: String?, val regnNo: String?, val ownerName: String?, val orderStatus: String?)
data class WalletSummary(val name: String, val balancePaise: Long, val heldPaise: Long, val creditLimitPaise: Long, val availablePaise: Long)
data class DealerHome(val dealer: DealerDto, val counts: Counts, val appointments: List<Appointment>, val wallet: WalletSummary?)
data class StatementEntry(val createdAt: String, val kind: String?, val memo: String?, val amountPaise: Long, val account: String)
data class Statement(val items: List<StatementEntry>)
data class WalletResponse(val summary: WalletSummary, val statement: Statement)

data class OrderDto(
    val id: String,
    val orderNo: String,
    val status: String,
    val regnNo: String,
    val chassisNo: String,
    val engineNo: String,
    val orderType: String,
    val plateSet: String,
    val vehicleClass: String?,
    val maker: String?,
    val fuel: String?,
    val amountPaise: Long?,
    val rejectionReason: String?,
    val remarks: String?,
    val createdAt: String,
    val ownerName: String?,
    val mobile: String?,
)
data class OrderPage(val items: List<OrderDto>, val total: Int)
data class CreateOrderRequest(
    val regnNo: String,
    val chassisNo: String,
    val engineNo: String,
    val orderType: String = "NEW",
    val plateSet: String = "BOTH",
    val oemCode: String? = null,
    val ownerName: String? = null,
    val mobile: String? = null,
)
data class CreatedOrder(val id: String, val orderNo: String, val status: String)
data class OemDto(val code: String, val name: String)

data class ChallanLine(val id: String, val orderNo: String?, val regnNo: String?, val position: String, val laserCode: String, val inwardStatus: String, val rejectReason: String?)
data class Challan(val id: String, val challanNo: String, val status: String, val createdAt: String, val plates: Int?, val lines: List<ChallanLine>?)
data class InwardLine(val lineId: String, val decision: String, val reason: String?)
data class InwardRequest(val lines: List<InwardLine>)

data class Allocation(val position: String, val laserCode: String)
data class PhotoRules(val front: Boolean, val rear: Boolean, val chassis: Boolean)
data class FitmentHit(val order: OrderDto, val allocations: List<Allocation>, val photoRules: PhotoRules, val canRecord: Boolean)

/** RFC 7807 problem returned by the API. */
data class Problem(val title: String?, val detail: String?, val type: String?, val errors: List<FieldError>?) {
    val code: String get() = type?.substringAfterLast('/') ?: ""
    fun message(): String = errors?.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.path}: ${it.message}" } ?: detail ?: title ?: "Request failed"
}
data class FieldError(val path: String, val message: String)

class ApiException(val status: Int, val problem: Problem?) : Exception(problem?.message() ?: "HTTP $status")

// ------------------------------------------------------------------------------------------- service

interface HsrpApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @GET("api/v1/dealer/home")
    suspend fun home(): Response<DealerHome>

    @GET("api/v1/dealer/wallet")
    suspend fun wallet(): Response<WalletResponse>

    @GET("api/v1/orders")
    suspend fun orders(@Query("q") q: String?, @Query("status") status: String?, @Query("pageSize") pageSize: Int = 50): Response<OrderPage>

    @GET("api/v1/orders/{id}")
    suspend fun order(@Path("id") id: String): Response<OrderDto>

    @POST("api/v1/orders")
    suspend fun createOrder(@Header("Idempotency-Key") key: String, @Body body: CreateOrderRequest): Response<CreatedOrder>

    @GET("api/v1/masters/oems")
    suspend fun oems(): Response<List<OemDto>>

    @GET("api/v1/dispatch/challans")
    suspend fun challans(@Query("status") status: String?): Response<List<Challan>>

    @GET("api/v1/dispatch/challans/{id}")
    suspend fun challan(@Path("id") id: String): Response<Challan>

    @POST("api/v1/dispatch/challans/{id}/inward")
    suspend fun inward(@Path("id") id: String, @Header("Idempotency-Key") key: String, @Body body: InwardRequest): Response<Challan>

    @GET("api/v1/fitments/search")
    suspend fun searchFitment(@Query("regnNo") regnNo: String): Response<List<FitmentHit>>

    @Multipart
    @POST("api/v1/fitments")
    suspend fun recordFitment(
        @Header("Idempotency-Key") key: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part photos: List<MultipartBody.Part>,
    ): Response<Map<String, Any>>
}

object ApiClient {
    private val gson = Gson()
    @Volatile private var cached: Pair<String, HsrpApi>? = null

    suspend fun get(settings: Settings): HsrpApi {
        val base = settings.baseUrl()
        cached?.let { if (it.first == base) return it.second }
        val auth = Interceptor { chain ->
            val token = runBlocking { settings.token() }
            val device = runBlocking { settings.deviceId() }
            val req = chain.request().newBuilder().header("X-Device-Id", device)
            if (token != null) req.header("Authorization", "Bearer $token")
            chain.proceed(req.build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
        val api = Retrofit.Builder()
            .baseUrl("$base/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(HsrpApi::class.java)
        cached = base to api
        return api
    }

    fun problem(res: Response<*>): Problem? = try {
        res.errorBody()?.string()?.let { gson.fromJson(it, Problem::class.java) }
    } catch (e: Exception) {
        null
    }
}

/** Returns the body or throws ApiException with the problem details. */
fun <T> Response<T>.unwrap(): T {
    if (isSuccessful) return body() ?: throw ApiException(code(), null)
    throw ApiException(code(), ApiClient.problem(this))
}

fun rupees(paise: Long?): String = if (paise == null) "–" else "₹" + String.format("%,.2f", paise / 100.0)
