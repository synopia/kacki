package org.synopia.kacki

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.gson.responseObject
import com.google.gson.Gson
import com.google.gson.JsonObject

data class LoginFlow(val type: String)
data class LoginResult(val flows: List<LoginFlow>)
data class LoginRequest(val type: String, val user: String, val password: String)
data class LoginResponse(val access_token: String, val home_server: String, val user_id: String)
data class Profile(val avatar_url: String, val displayname: String?)
data class Whoami(val user_id: String)

data class SyncResult(val next_batch: String, val rooms: Rooms)
data class Rooms(val invite: Map<String, Invitation>, val join: Map<String, Room>)
data class Room(val timeline: TimeLine, val state: RoomState)
data class Invitation(val invite_state: TimeLine)
data class TimeLine(val events: List<Event>, val limited: Boolean, val pref_batch: String)
data class RoomState(val events: List<Event>)
data class Event(val content: JsonObject, val event_id: String, val membership: String, val origin_server_ts: String, val sender: String, val state_key: String, val type: String)
data class RoomMessage(val body: String, val msgtype: String)
data class JoinResponse(val room_id: String)
data class ChunkedResponse(val chunk: List<Event>, val start: String, val end: String)
data class SendMessageResponse(val event_id: String)
data class RoomName(val name: String)
class MatrixClient(val baseUrl: String) {
    val fuelManager = FuelManager.instance
    var userId: String? = null
    var accessToken: String? = null
    var lastBatch: String? = null
    var messagesToken: String? = null

    suspend fun login(user: String, password: String) {
        println("Trying to login")
        request<LoginResult>(Method.GET, "login")

        val req = LoginRequest("m.login.password", user, password)
        val loginResponse = request<LoginRequest, LoginResponse>(Method.POST, "login", req)

        userId = loginResponse?.user_id
        accessToken = loginResponse?.access_token

        if (accessToken != null) {
            println("...success")
        } else {
            println("...failed")
        }
    }

    suspend fun profile(userId: String): Profile {
        println("loading profile $userId")
        return request(Method.GET, "profile/$userId")!!
    }

    suspend fun join(roomId: String): JoinResponse {
        println("joining $roomId")
        return request(Method.POST, "rooms/$roomId/join")!!
    }

    suspend fun members(roomId: String): List<Event> {
        println("members $roomId")
        val resp = request<ChunkedResponse>(Method.GET, "rooms/$roomId/members")
        return resp!!.chunk
    }

    suspend fun send(roomId: String, body: String) {
        println("sending $roomId: $body")
        request<RoomMessage, SendMessageResponse>(Method.POST, "rooms/$roomId/send/m.room.message", RoomMessage(body, "m.text"))
    }

    suspend fun online() {
        request<JsonObject>(Method.PUT, "presence/$userId/status", listOf(Pair("presenceState", "online")))
    }

    suspend fun messages(roomId: String, limit: Int): List<Event> {
        println("messages $roomId")
        if (messagesToken == null) {
            messagesToken = lastBatch
        }
        val resp = request<ChunkedResponse>(Method.GET, "rooms/$roomId/messages", listOf(Pair("from", messagesToken), Pair("limit", limit), Pair("dir", "b")))
        messagesToken = resp!!.end
        return resp.chunk
    }

    suspend fun sync(wait: Boolean): SyncResult? {
        println("sync ${if (wait) "wait" else "-"}")
        val timeout = if (wait) 30000 else 0
        try {
            val result = request<SyncResult>(Method.GET, "sync", listOf(Pair("since", lastBatch), Pair("timeout", timeout)))!!
            lastBatch = result.next_batch
            return result
        } catch (e: Exception) {
            return null
        }
    }

    private suspend inline fun <reified O : Any> request(method: Method, url: String, params: List<Pair<String, Any?>>? = null): O? {
        return request(method, url, params, {})
    }

    private suspend inline fun <I, reified O : Any> request(method: Method, url: String, body: I, params: List<Pair<String, Any?>>? = null): O? {
        return request(method, url, params, {
            it.body(Gson().toJson(body))
        })
    }

    private suspend inline fun <reified O : Any> request(method: Method, url: String, params: List<Pair<String, Any?>>? = null, prepare: (Request) -> Unit): O? {
        val request = if (accessToken != null) {
            var finalUrl = "$baseUrl/$url?access_token=$accessToken&"
            if (params != null) {
                finalUrl += params.filter { it.second != null }.map { "${it.first}=${it.second}" }.joinToString("&")
            }
            fuelManager.request(method, finalUrl)
        } else {
            var finalUrl = "$baseUrl/$url?"
            if (params != null) {
                finalUrl += params.map { "${it.first}=${it.second}" }.joinToString("&")
            }
            fuelManager.request(method, finalUrl)
        }

        prepare(request)

        val (_, _, result) = request.responseObject<O>()
        val error = result.component2()
        if (error != null) {
            throw error
        } else {
            return result.component1()
        }

    }
}
