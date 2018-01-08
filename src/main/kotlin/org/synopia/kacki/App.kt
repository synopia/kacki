package org.synopia.kacki

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import kotlinx.coroutines.experimental.async
import org.synopia.kacki.plugins.BotsnackPlugin
import org.synopia.kacki.plugins.ChuckNorrisPlugin
import org.synopia.kacki.plugins.MarkovPlugin

class App(args: ArgParser) {
    val baseUrl by args
            .storing("-c", "--client-api", help = "URL of matrix client rest API (default is official one)")
            .default("https://matrix.org/_matrix/client/r0https://matrix.org/_matrix/client/r0")
    val user by args.storing("-u", "--user", help = "username to login")
    val password by args.storing("-p", "--password", help = "password to login")

    fun start() {
        val config = KackiConfig(baseUrl, user, password)

        val kacki = Kacki(config)
        kacki.plugins += BotsnackPlugin()
        kacki.plugins += ChuckNorrisPlugin()
        kacki.plugins += MarkovPlugin()
        async {
            kacki.init()
            while (true) {
                kacki.sync()
            }
        }

        while (true) {
            Thread.sleep(1000)
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            App(ArgParser(args)).start()

        }
    }
}

//fun main(args: Array<String>) {
//    val json = Files.readAllLines(Paths.get("invite.json")).joinToString("\n")
//    val sync = Gson().fromJson<SyncResult>(json)
//    println(sync)
//
//}

