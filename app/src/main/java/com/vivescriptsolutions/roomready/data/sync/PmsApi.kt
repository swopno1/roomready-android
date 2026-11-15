package com.vivescriptsolutions.roomready.data.sync

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed interface ApiOutcome<out T> {
  data class Ok<T>(val value: T) : ApiOutcome<T>

  data class Http(val code: Int, val error: String?) : ApiOutcome<Nothing>

  data class Network(val exception: IOException) : ApiOutcome<Nothing>

  data class Malformed(val message: String) : ApiOutcome<Nothing>

  data object NotConfigured : ApiOutcome<Nothing>
}

/**
 * PMS transport. Every request carries `Authorization: Bearer <key>`.
 * The key is read fresh from [ApiKeyStore] per call and never logged.
 */
class PmsApi(
  private val baseUrlProvider: suspend () -> String?,
  private val keyProvider: suspend () -> String?,
  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .build(),
) {
  private suspend fun baseUrl(): String? = baseUrlProvider()?.trim()?.trimEnd('/')

  private suspend fun <T> get(path: String, parse: (String) -> T): ApiOutcome<T> =
    withContext(Dispatchers.IO) {
      val base = baseUrl()
      val key = keyProvider()
      if (base.isNullOrBlank() || key.isNullOrBlank()) return@withContext ApiOutcome.NotConfigured
      val request =
        Request.Builder()
          .url(base + path)
          .header("Authorization", "Bearer $key")
          .header("Accept", "application/json")
          .get()
          .build()
      execute(request, parse)
    }

  private suspend fun <T> post(path: String, json: JSONObject, parse: (String) -> T): ApiOutcome<T> =
    withContext(Dispatchers.IO) {
      send("POST", path, json, parse)
    }

  private suspend fun <T> patch(path: String, json: JSONObject, parse: (String) -> T): ApiOutcome<T> =
    withContext(Dispatchers.IO) {
      send("PATCH", path, json, parse)
    }

  private suspend fun <T> send(
    method: String,
    path: String,
    json: JSONObject,
    parse: (String) -> T,
  ): ApiOutcome<T> {
    val base = baseUrl()
    val key = keyProvider()
    if (base.isNullOrBlank() || key.isNullOrBlank()) return ApiOutcome.NotConfigured
    val request =
      Request.Builder()
        .url(base + path)
        .header("Authorization", "Bearer $key")
        .header("Accept", "application/json")
        .method(method, json.toString().toRequestBody("application/json".toMediaType()))
        .build()
    return execute(request, parse)
  }

  private fun <T> execute(request: Request, parse: (String) -> T): ApiOutcome<T> {
    val response =
      try {
        client.newCall(request).execute()
      } catch (e: IOException) {
        return ApiOutcome.Network(e)
      }
    response.use { res ->
      val body = res.body.string()
      if (!res.isSuccessful) return ApiOutcome.Http(res.code, parseErrorMessage(body))
      return try {
        ApiOutcome.Ok(parse(body))
      } catch (e: MalformedPayloadException) {
        ApiOutcome.Malformed(e.message ?: "Malformed response")
      }
    }
  }

  suspend fun bootstrap(): ApiOutcome<BootstrapPayload> =
    get("/api/housekeeping/bootstrap", ::parseBootstrap)

  suspend fun refreshRooms(): ApiOutcome<RoomsPayload> =
    get("/api/housekeeping/rooms", ::parseRooms)

  suspend fun postStatus(
    unitId: String,
    expected: String,
    action: String,
    staffName: String,
    note: String?,
  ): ApiOutcome<StatusResult> {
    val json =
      JSONObject()
        .put("expected", expected)
        .put("action", action)
        .put("staffName", staffName)
    if (!note.isNullOrBlank()) json.put("note", note)
    return post("/api/housekeeping/rooms/$unitId/status", json, ::parseStatusResult)
  }

  suspend fun getOpenTickets(): ApiOutcome<List<PmsTicket>> =
    get("/api/housekeeping/tickets?status=OPEN", ::parseTickets)

  suspend fun postTicket(
    unitId: String,
    title: String,
    description: String,
    priority: String,
    staffName: String,
  ): ApiOutcome<PmsTicket> {
    val json =
      JSONObject()
        .put("unitId", unitId)
        .put("title", title)
        .put("description", description)
        .put("priority", priority)
        .put("staffName", staffName)
    return post("/api/housekeeping/tickets", json, ::parseSingleTicket)
  }

  suspend fun patchTicket(id: String, status: String, staffName: String): ApiOutcome<PmsTicket> {
    val json =
      JSONObject()
        .put("status", status)
        .put("staffName", staffName)
    return patch("/api/housekeeping/tickets/$id", json, ::parseSingleTicket)
  }
}
