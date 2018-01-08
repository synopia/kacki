package org.synopia.kacki.plugins

import kotlinx.coroutines.experimental.async
import org.synopia.kacki.KackiMember
import org.synopia.kacki.KackiPlugin
import org.synopia.kacki.KackiRoom

class BotsnackPlugin : KackiPlugin {
    override fun help(): String {
        return "botsnack => reward me for being good"
    }

    override fun onMessage(room: KackiRoom, sender: KackiMember, text: String) {
        if (text == "botsnack") {
            if (room.pm) {
                async { room.send("Thanks") }
            } else {
                async { room.send("Thanks ${sender.nickname}") }
            }
        }
    }
}