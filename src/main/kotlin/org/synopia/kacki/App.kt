package org.synopia.kacki

import com.xenomachina.argparser.ArgParser
import kotlinx.coroutines.experimental.async
import org.synopia.kacki.plugins.BotsnackPlugin
import org.synopia.kacki.plugins.ChuckNorrisPlugin
import org.synopia.kacki.plugins.MarkovPlugin

class App(args: ArgParser) {
    val user by args.storing("-u", "--user", help = "username to login")
    val password by args.storing("-p", "--password", help = "password to login")

    fun start() {
        val config = KackiConfig("https://matrix.org/_matrix/client/r0", user, password)

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

