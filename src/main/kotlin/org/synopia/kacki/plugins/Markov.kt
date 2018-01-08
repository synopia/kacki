package org.synopia.kacki.plugins

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import kotlinx.coroutines.experimental.async
import org.synopia.kacki.*
import java.util.*

data class ChainEntry(var total: Int, val hash: MutableMap<String, Int>)
class MarkovChain3() {
    companion object {
        val MARKER = "\r\n"
    }

    val chains = mutableMapOf<String, ChainEntry>()
    val rChains = mutableMapOf<String, ChainEntry>()
    val rng = Random()

    fun pickWord(map: Map<String, Int>, rng: Random): String {
        val total = map.values.sum()
        var hit = rng.nextInt(total)

        for (key in map.keys) {
            hit -= map[key]!!
            if (hit < 0) {
                return key
            }

        }
        return ""
    }

    fun learnTriplet(word1: String, word2: String, word3: String) {
        val k = "$word1 $word2"
        val rk = "$word2 $word3"
        var total = 0
        var hash = mutableMapOf<String, Int>()
        if (chains.containsKey(k)) {
            val entry = chains[k]!!
            total += entry.total
            hash.putAll(entry.hash)
        }
        hash[word3] = (hash[word3] ?: 0) + 1
        total += 1
        chains[k] = ChainEntry(total, hash)

        total = 0
        hash = mutableMapOf()
        if (rChains.containsKey(rk)) {
            val entry = rChains[rk]!!
            total += entry.total
            hash.putAll(entry.hash)
        }
        hash[word1] = (hash[word1] ?: 0) + 1
        total += 1
        rChains[rk] = ChainEntry(total, hash)
    }

    fun parse(content: List<String>) {
        content.forEach {
            val words = it.toLowerCase().split(" ").map { it.trim() }.filter { it != "" }
            var word1 = MARKER
            var word2 = MARKER
            words.forEach { word3 ->
                learnTriplet(word1, word2, word3)
                word1 = word2
                word2 = word3
            }
        }
    }

    fun pickWord(word1: String, word2: String): String {
        val k = "$word1 $word2"
        if (!chains.containsKey(k)) {
            return MARKER
        }
        return pickWord(chains[k]!!.hash, rng)
    }

    fun chatAbout(nWords: Int, text: String): String {
        val words = text.split(" ")
        if (words.size < 2) {
            return chat(nWords, text, null)
        } else {
            var seq = listOf<Pair<String, String>>()
            (0..words.size - 2).forEach {
                seq += Pair(words[it], words[it + 1])
            }
            for (p in seq.shuffled(rng)) {
                val res = chat(nWords, p.first, p.second)
                if (!text.contains(res)) {
                    return res
                }
            }
            for (w in words.shuffled()) {
                val res = chat(nWords, w, null)
                if (!text.contains(res)) {
                    return res
                }
            }
        }
        return MARKER
    }

    fun randomChat(nWords: Int): String {
        var word1 = MARKER
        var word2 = MARKER
        var output = emptyList<String>()
        for (i in 0 until nWords) {
            val word3 = pickWord(word1, word2)
            if (word3 == MARKER) {
                break
            }
            output += word3
            word1 = word2
            word2 = word3
        }
        return output.joinToString(" ")
    }

    fun chat(nWords: Int, word1: String, word2: String?): String {
        val output = if (word2 != null) {
            mutableListOf(word1, word2)
        } else {
            val entries = chains.entries.filter { it.key.contains(word1) }
            if (entries.isEmpty()) {
                return ""
            }
            entries.elementAt(rng.nextInt(entries.size)).key.split(" ").toMutableList()
        }
        while (output.size < nWords && (output.first() != MARKER || output.last() != MARKER)) {
            if (output.last() != MARKER) {
                output.add(pickWord(output[output.size - 2], output[output.size - 1]))
            }
            if (output.first() != MARKER) {
                output.add(0, pickWord(output[0], output[1]))
            }
        }
        output.remove(MARKER)
        return output.joinToString(" ").trim()
    }
}

class MarkovPlugin : KackiPlugin {
    val markov = MarkovChain3()
    val maxWords = 10
    var lastSaid = ""
    var probability: Double = 0.05
    val rng = Random()
    override fun onHistory(room: KackiRoom, events: List<Event>) {
        val lines = events
                .filter { it.type == "m.room.message" }
                .map {
                    val body = Gson().fromJson<RoomMessage>(it.content)
                    if (body.msgtype == "m.text" && it.sender != "@kacki:matrix.org") {
                        body.body
                    } else {
                        null
                    }
                }.filterNotNull()
        markov.parse(lines)
    }

    override fun help(): String {
        return "markov plugin: listens to chat to build a markov chain, with which it can (perhaps) attempt to (inanely) contribute to 'discussion'. Sort of.. Will get a *lot* better after listening to a lot of chat. Usage: 'chat' to attempt to say something relevant to the last line of chat, if it can"
    }

    override fun onMessage(room: KackiRoom, sender: KackiMember, text: String) {
        if (text == "chat") {
            val answer = markov.randomChat(maxWords)
            async { room.send(answer) }
            return
        } else if (text == "chatabout") {
            val answer = markov.chatAbout(maxWords, lastSaid)
            async { room.send(answer) }
        } else if (text.startsWith("chat")) {
            val splitted = text.split(" ")
            if (splitted.size > 2) {
                async { room.send(markov.chat(maxWords, splitted[1], splitted[2])) }
                return
            }
        }
        markov.parse(listOf(text))
        lastSaid = text

        if (rng.nextDouble() < probability) {
            val answer = markov.chatAbout(maxWords, lastSaid)
            async { room.send(answer) }
        }
    }
}
