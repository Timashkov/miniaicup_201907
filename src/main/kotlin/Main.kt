import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList

fun getRandom(commands: Array<String>): String {
    val rnd = Random().nextInt(commands.size)
    return commands[rnd]
}

val f = File("test1")

fun main(args: Array<String>) {
    try {
        val commands = arrayOf("left", "right", "up", "down")
        f.appendText("1\n")

        val firstInput = readLine()
        f.appendText(firstInput + "\n")

        val config = WorldConfig.build(JSONObject(firstInput)) ?: throw IllegalArgumentException(firstInput)
        f.appendText(config.toString() + "\n")

        while (true) {
            val input = readLine() ?: return

            f.appendText("$input\n")
            val tickData = TickData.build(input)
            f.appendText("$tickData\n")

            val command = getRandom(commands)
            println("{\"command\": \"$command\"}")
            f.appendText("$command\n")
        }
    } catch (e: Exception) {
        System.err.println(e)
        e.printStackTrace()
    }
}

data class TickData(val enemies: ArrayList<PlayerTickData>, val me: PlayerTickData, val tickNum: Int, val bonuses: BonusesList) {

    companion object {
        fun build(line: String): TickData? {
            val jsTick = JSONObject(line)
            if (jsTick.getString("type") != "tick")
                return null
            val en = ArrayList<PlayerTickData>()
            lateinit var me: PlayerTickData
            val players = jsTick.getJSONObject("params").getJSONObject("players")
            players.keys().forEach { key ->
                if (key == "i") {
                    me = PlayerTickData.build("i", players.getJSONObject(key))
                } else {
                    en.add(PlayerTickData.build(key, players.getJSONObject(key)))
                }
            }

            return TickData(en, me,
                    jsTick.getJSONObject("params").getInt("tick_num"),
                    BonusesList.build(jsTick.getJSONObject("params").getJSONArray("bonuses"))
            )
        }
    }

}

data class PlayerTickData(val name: String, val score: Int, val direction: Direction, val territory: VertexList, val
lines: VertexList,
                          val position: Vertex, val bonuses: BonusesList) {
    companion object {
        fun build(name: String, tickData: JSONObject) = PlayerTickData(
                name,
                tickData.getInt("score"),
                Direction.valueOf(tickData.getString("direction")),
                VertexList.build(tickData.getJSONArray("territory")),
                VertexList.build(tickData.getJSONArray("lines")),
                Vertex.build(tickData.getJSONArray("position")),
                BonusesList.build(tickData.getJSONArray("bonuses"))
        )
    }
}

data class BonusesList(val items: ArrayList<Bonus>) {
    companion object {
        fun build(jsBonuses: JSONArray, known: ArrayList<Bonus>? = null): BonusesList {
            val dest: ArrayList<Bonus> = known ?: ArrayList()
            dest.clear()
            jsBonuses.forEach {
                if (it is JSONObject)
                    dest.add(Bonus.build(it))
            }
            return BonusesList(dest)
        }
    }
}

data class Bonus(val type: BonusType, var ticks: Int, var position: Vertex) {
    companion object {
        fun build(jsBonus: JSONObject): Bonus {
            val type = when (jsBonus.getString("type")) {
                "n" -> BonusType.NITRO
                "s" -> BonusType.SLOW
                else -> BonusType.SAW
            }
            val ticks = if (jsBonus.has("ticks")) jsBonus.getInt("ticks") else -1
            val pos = if (jsBonus.has("position")) Vertex.build(jsBonus.getJSONArray("position")) else Vertex.UNDEFINED
            return Bonus(type, ticks, pos)
        }
    }
}

enum class BonusType(val literal: String) {
    NITRO("n"), SLOW("s"), SAW("saw")
}

enum class Direction() {
    left, up, right, down;
}

data class VertexList(val vertexes: ArrayList<Vertex>) {
    companion object {
        fun build(jsArray: JSONArray, vertexes: ArrayList<Vertex>? = null): VertexList {
            val dest: ArrayList<Vertex> = vertexes ?: ArrayList()
            dest.clear()
            return if (jsArray.length() == 0) {
                VertexList(ArrayList())
            } else {
                jsArray.forEach {
                    if (it is JSONArray) {
                        dest.add(Vertex.build(it))
                    }
                }
                VertexList(dest)
            }
        }
    }
}

data class Vertex(val x: Int, val y: Int) {
    companion object {
        fun build(jsVertex: JSONArray) = Vertex(jsVertex.getInt(0), jsVertex.getInt(1))
        val UNDEFINED = Vertex(-1, -1)
    }
}

data class WorldConfig(val xCellsCount: Int, val yCellsCount: Int, val defaultSpeed: Int, val squareWith: Int) {
    companion object {
        fun build(firstTickData: JSONObject): WorldConfig? {
            if (firstTickData.getString("type") != "start_game") {
                return null
            }
            val configJson = firstTickData.getJSONObject("params")
            return WorldConfig(configJson.getInt("x_cells_count"),
                    configJson.getInt("y_cells_count"),
                    configJson.getInt("speed"),
                    configJson.getInt("width"))
        }
    }

    override fun toString(): String {
        return "xCells: $xCellsCount, yCells: $yCellsCount, defaultSpeed: $defaultSpeed, width: $squareWith"
    }
}