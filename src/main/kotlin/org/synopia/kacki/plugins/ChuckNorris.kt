package org.synopia.kacki.plugins

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import kotlinx.coroutines.experimental.async
import org.synopia.kacki.KackiMember
import org.synopia.kacki.KackiPlugin
import org.synopia.kacki.KackiRoom

data class IcndbJoke(val id: Int, val joke: String)
data class IcndbEntry(val type: String, val value: IcndbJoke)

class ChuckNorrisPlugin : KackiPlugin {
    override fun help(): String {
        return "chuck|norris|chucknorris => show a random Chuck Norris fact"
    }

    override fun onMessage(room: KackiRoom, sender: KackiMember, text: String) {
        if (text == "chuck" || text == "norris" || text == "chucknorris") {
            val (_, _, joke) = Fuel.get("http://api.icndb.com/jokes/random").responseObject<IcndbEntry>()
            val text = joke.component1()?.value?.joke
            if (text != null) {
                async { room.send(text) }
            }
        }
    }
}