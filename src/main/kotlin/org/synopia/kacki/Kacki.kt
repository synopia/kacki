package org.synopia.kacki

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import kotlinx.coroutines.experimental.async

data class KackiConfig(val baseUrl: String, val user: String, val password: String)
data class KackiMember(val nickname: String, var userIds: List<String>, var hasPM: Boolean = false) {
    var admin: Boolean = false
}

data class KackiRoom(val matrix: MatrixClient, val roomId: String, val name: String, val pm: Boolean) {
    suspend fun send(msg: String) {
        matrix.send(roomId, msg)
    }
}

interface KackiPlugin {
    fun onHistory(room: KackiRoom, events: List<Event>) {}
    fun help(): String
    fun onMessage(room: KackiRoom, sender: KackiMember, text: String)
}

class Kacki(val config: KackiConfig) {
    var members = mutableMapOf<String, KackiMember>()
    var rooms = mutableMapOf<String, KackiRoom>()

    val matrix = MatrixClient(config.baseUrl)
    var plugins = emptyList<KackiPlugin>()

    suspend fun init() {
        matrix.login(config.user, config.password)
        startSync()
        matrix.online()
        async {
            val allMessages = mutableMapOf<String, List<Event>>()
            rooms.values.forEach { room ->
                var list = listOf<Event>()
                var evts: List<Event>
                do {
                    evts = matrix.messages(room.roomId, 100)
                    list += evts
                } while (evts.isNotEmpty())
                allMessages[room.roomId] = list
                println("All messages fetched for room $room (${list.size})")
                plugins.forEach {
                    it.onHistory(room, list)
                }
            }
        }
    }

    suspend fun startSync() {
        val sync = matrix.sync(false)
        if (sync != null) {
            processRooms(sync)
            processInvites(sync)
            processMembers(sync)
        }
    }

    suspend fun sync() {
        val sync = matrix.sync(true)
        if (sync != null) {
            processRooms(sync)
            processInvites(sync)
            processMembers(sync)

            processMessages(sync)
        }
    }


    suspend fun onInvite(roomId: String, userId: String, pm: Boolean) {
        matrix.join(roomId)
    }

    suspend fun onMessage(roomId: String, userId: String, msg: String) {
        if (userId == matrix.userId) {
            return
        }
        val member = members.values.first { it.userIds.contains(userId) }
        val room = rooms.values.first { it.roomId == roomId }
        val text = msg.trim()
        plugins.forEach {
            it.onMessage(room, member, text)
        }
    }

    private fun processRooms(sync: SyncResult) {
        sync.rooms.join.forEach { id, room ->
            val members = room.state.events.filter { it.type == "m.room.member" }
            if (members.size == 2) {
                val other = members.first { it.state_key != matrix.userId }
                val profile = Gson().fromJson<Profile>(other.content)
                createRoom(id, profile.displayname!!, true)
            } else {
                val json = room.state.events.firstOrNull { it.type == "m.room.name" }
                if (json != null) {
                    val name = Gson().fromJson<RoomName>(json.content).name
                    createRoom(id, name, false)
                }
            }
        }
    }

    private fun createRoom(roomId: String, name: String, pm: Boolean) {
        val old = rooms[name]
        if (old == null) {
            rooms[name] = KackiRoom(matrix, roomId, name, pm)
        }
    }

    private fun processMembers(sync: SyncResult) {
        sync.rooms.join.forEach { id, room ->
            val pm = room.state.events.count { it.type == "m.room.member" } == 2
            room.state.events.filter { it.type == "m.room.member" && it.membership == "join" }.forEach {
                createMember(it, pm)
            }
        }
    }

    private fun createMember(it: Event, pm: Boolean) {
        val profile = Gson().fromJson<Profile>(it.content)
        if (profile.displayname == null) {
            return
        }
        val name = if (profile.displayname.endsWith(" (IRC)")) {
            profile.displayname.replace(" (IRC)", "").trim()
        } else {
            profile.displayname.trim()
        }
        val member = members[name]
        if (member == null) {
            members[name] = KackiMember(name, listOf(it.state_key), pm)
        } else {
            if (!member.userIds.contains(it.state_key)) {
                members[name] = KackiMember(name, member.userIds + it.state_key, pm || member.hasPM)
            }
        }
    }

    suspend private fun processMessages(sync: SyncResult) {
        sync.rooms.join.forEach { id, room ->
            val newMessages = room.timeline.events.filter { it.type == "m.room.message" }
            newMessages.forEach {
                val body = Gson().fromJson<RoomMessage>(it.content)
                if (body.msgtype == "m.text" && it.sender != matrix.userId) {
                    async {
                        onMessage(id, it.sender, body.body)
                    }
                }
            }
        }
    }

    suspend private fun processInvites(sync: SyncResult) {
        sync.rooms.invite.forEach { id, room ->
            val members = room.invite_state.events.filter { it.type == "m.room.member" }
            val pm = members.size == 2
            val event = members.first { it.state_key == matrix.userId }
            async {
                onInvite(id, event.sender, pm)
            }
        }
    }
}