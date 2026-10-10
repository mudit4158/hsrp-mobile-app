package com.hsrp.dealer.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.hsrp.dealer.data.ApiClient
import com.hsrp.dealer.data.AppDb
import com.hsrp.dealer.data.InwardRequest
import com.hsrp.dealer.data.PendingAction
import com.hsrp.dealer.data.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Fitment fields queued on the device (photos are files in app storage). */
data class FitmentPayload(val orderId: String, val frontLaserCode: String?, val rearLaserCode: String?, val fittedAt: String)

object SyncQueue {
    private val gson = Gson()

    suspend fun enqueueFitment(context: Context, label: String, payload: FitmentPayload, front: String?, rear: String?, chassis: String?): Long {
        val dao = AppDb.get(context).pending()
        dao.findActive(PendingAction.TYPE_FITMENT, payload.orderId)?.let { return it.id }
        val id = dao.insert(
            PendingAction(
                type = PendingAction.TYPE_FITMENT,
                refId = payload.orderId,
                label = label,
                idempotencyKey = UUID.randomUUID().toString(),
                payload = gson.toJson(payload),
                photoFront = front,
                photoRear = rear,
                photoChassis = chassis,
            ),
        )
        kick(context)
        return id
    }

    suspend fun enqueueInward(context: Context, challanId: String, label: String, request: InwardRequest): Long {
        val dao = AppDb.get(context).pending()
        dao.findActive(PendingAction.TYPE_INWARD, challanId)?.let { return it.id }
        val id = dao.insert(
            PendingAction(type = PendingAction.TYPE_INWARD, refId = challanId, label = label, idempotencyKey = UUID.randomUUID().toString(), payload = gson.toJson(request)),
        )
        kick(context)
        return id
    }

    /** Runs the sync worker as soon as there is a network connection. */
    fun kick(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("hsrp-sync", ExistingWorkPolicy.REPLACE, request)
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val gson = Gson()

    override suspend fun doWork(): Result {
        val dao = AppDb.get(applicationContext).pending()
        val api = ApiClient.get(Settings(applicationContext))
        var retry = false
        for (a in dao.due()) {
            dao.update(a.copy(status = PendingAction.STATUS_SYNCING, updatedAt = System.currentTimeMillis()))
            try {
                val res: Response<*> = when (a.type) {
                    PendingAction.TYPE_FITMENT -> sendFitment(a)
                    PendingAction.TYPE_INWARD -> api.inward(a.refId, a.idempotencyKey, gson.fromJson(a.payload, InwardRequest::class.java))
                    else -> null
                } ?: continue
                val problem = if (res.isSuccessful) null else ApiClient.problem(res)
                val now = System.currentTimeMillis()
                when {
                    // Done, or the server already has it (e.g. the first attempt's response was lost).
                    res.isSuccessful || (problem?.code ?: "") in setOf("fitment-exists", "already-inwarded") ->
                        dao.update(a.copy(status = PendingAction.STATUS_DONE, attempts = a.attempts + 1, lastError = null, updatedAt = now))
                    res.code() == 401 -> {
                        dao.update(a.copy(status = PendingAction.STATUS_PENDING, attempts = a.attempts + 1, lastError = "Signed out. Sign in again to send.", updatedAt = now))
                    }
                    res.code() in 400..499 && res.code() != 408 && res.code() != 429 ->
                        dao.update(a.copy(status = PendingAction.STATUS_FAILED, attempts = a.attempts + 1, lastError = problem?.message() ?: "HTTP ${res.code()}", updatedAt = now))
                    else -> {
                        dao.update(a.copy(status = PendingAction.STATUS_PENDING, attempts = a.attempts + 1, lastError = "Server error ${res.code()}; will retry", updatedAt = now))
                        retry = true
                    }
                }
            } catch (e: IOException) {
                dao.update(a.copy(status = PendingAction.STATUS_PENDING, attempts = a.attempts + 1, lastError = "No connection to the server; will retry", updatedAt = System.currentTimeMillis()))
                retry = true
            }
        }
        return if (retry) Result.retry() else Result.success()
    }

    private suspend fun sendFitment(a: PendingAction): Response<*> {
        val api = ApiClient.get(Settings(applicationContext))
        val p = gson.fromJson(a.payload, FitmentPayload::class.java)
        val text = "text/plain".toMediaType()
        val fields = mutableMapOf<String, RequestBody>(
            "orderId" to p.orderId.toRequestBody(text),
            "fittedAt" to p.fittedAt.toRequestBody(text),
        )
        p.frontLaserCode?.let { fields["frontLaserCode"] = it.toRequestBody(text) }
        p.rearLaserCode?.let { fields["rearLaserCode"] = it.toRequestBody(text) }
        val jpeg = "image/jpeg".toMediaType()
        val parts = listOfNotNull(
            a.photoFront?.let { File(it) }?.takeIf { it.exists() }?.let { MultipartBody.Part.createFormData("front", it.name, it.asRequestBody(jpeg)) },
            a.photoRear?.let { File(it) }?.takeIf { it.exists() }?.let { MultipartBody.Part.createFormData("rear", it.name, it.asRequestBody(jpeg)) },
            a.photoChassis?.let { File(it) }?.takeIf { it.exists() }?.let { MultipartBody.Part.createFormData("chassis", it.name, it.asRequestBody(jpeg)) },
        )
        return api.recordFitment(a.idempotencyKey, fields, parts)
    }
}
