package org.synopia.kacki.plugins


/*
data class RouletteHistory(var games:Int, var shots:Int, var deaths:Int, var misses:Int, var wins:Int, var points:Int)

class Roulette(val registry:MutableMap<String, Any>) {
    lateinit var matrixClient: MatrixClient
    var chambers = Stack<Boolean>()
    var last : String?=null
    var players = emptyList<String>()
    val random = Random()
    init {
        resetChambers()
    }
    fun onMessage(roomId: String, userId:String, msg:String) {
        val totals = registry["totals"] as RouletteHistory? ?: RouletteHistory(0,0,0,0,0,0)
        val playerdata = registry["player_$userId"] as RouletteHistory? ?: RouletteHistory(0,0,0,0,0,0)
        if( last==userId ) {
            matrixClient.send(roomId, "You can't go twice in a row!")
            return
        }
        last = userId
        if( !players.contains(userId)) {
            players += userId
            playerdata.games ++
        }
        playerdata.shots++
        totals.shots++

        val shot = chambers.pop()
        val chamberNo = 6-chambers.size
        if( shot ) {
            matrixClient.send(roomId, "$userId: chamber $chamberNo of 6 => *BANG*")
            playerdata.deaths ++
            totals.deaths ++
            players.filter { it!=userId }.forEach {
                val pdata = registry["player_$it"] as RouletteHistory?
                if( pdata!=null ) {
                    pdata.wins++
                    totals.wins++
                }
            }
            players = emptyList()
            last = null
        } else {
            matrixClient.send(roomId, "$userId: chamber $chamberNo of 6 => +click+")
            playerdata.misses ++
            playerdata.points += 1.shl(chamberNo)
            totals.misses++
        }
        registry["player_$userId"] = playerdata
        registry["totals"] = totals

        if( shot || chambers.isEmpty() ) {
            reload(roomId)
        } else if( chambers.size==1 ) {
            spin(roomId, userId)
        }
    }

    fun reload(roomId: String) {
        matrixClient.send(roomId, "*reloads*")
        resetChambers()
        val totals = registry["totals"] as RouletteHistory? ?: RouletteHistory(0,0,0,0,0,0)
        players.forEach {
            val pdata = registry["player_$it"] as RouletteHistory?
            if( pdata!=null ) {
                pdata.wins++
                totals.wins++
            }
        }
        totals.games++
        registry["totals"] = totals
        players = emptyList()
        last=null
    }

    fun spin(roomId: String, userId: String) {
        matrixClient.send(roomId, "*spins the cylinder*")
        resetChambers()
    }

    fun resetChambers() {
        chambers.clear()
        (1..6).forEach { chambers.push(false) }
        chambers[random.nextInt(6)] = true
    }
}
*/