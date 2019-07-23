import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.IllegalArgumentException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

val f = File("test1")

fun main(args: Array<String>) {
    try {
        f.appendText("1\n")

        val firstInput = readLine()
        f.appendText(firstInput + "\n")

        val config = WorldConfig.build(JSONObject(firstInput)) ?: throw IllegalArgumentException(firstInput)
        f.appendText(config.toString() + "\n")

        val strategy = SimpleStrategy(config)

        while (true) {
            val input = readLine() ?: return

            f.appendText("$input\n")
            val tickData = TickData.build(input) ?: return
            f.appendText("$tickData\n")
            strategy.onNewTick(tickData)

            val command = strategy.getCommand()
            println("{\"command\": \"$command\"}")
            f.appendText("$command\n")
        }
    } catch (e: Exception) {
        System.err.println(e)
        e.printStackTrace()
    }
}

class SimpleStrategy(val config: WorldConfig) {

    private val commands = arrayOf("left", "right", "up", "down")

    lateinit var currentTick: TickData
    private lateinit var lastKnownDirection: Direction
    private var goHomeRequired = false

    fun onNewTick(tickData: TickData) {
        this.currentTick = tickData
    }

    private fun getNearestMyTerritoryPoint(): NearestVertex {
        var min = 1000000
        var target = Vertex(0, 0)
        currentTick.me.territory.vertexes.forEach {
            val test = abs(currentTick.me.position.x - it.x) + abs(currentTick.me.position.y - it.y)
            if (test < min) {
                min = test
                target = it
            }
        }

        val dir = if (abs(currentTick.me.position.x - target.x) > abs(currentTick.me.position.y - target.y)) {
            if (currentTick.me.position.x > target.x) {
                Direction.down
            }
            Direction.up
        } else {
            if (currentTick.me.position.y > target.y) {
                Direction.left
            }
            Direction.right
        }

        return NearestVertex(target, (config.yCellsCount *
                config.squareWith - currentTick.me.position.y) / config.defaultSpeed + (config.xCellsCount *
                config.squareWith - currentTick.me.position.x) / config.defaultSpeed, dir)
    }


    fun getCommand(): String {
        return if (currentTick.tickNum == 1) {
            val r = getRandom(commands.copyOfRange(0, 1))
            f.appendText("GetCommand 1 $r\n")
            r
        } else {
            val r = getNextTurn()
            f.appendText("GetCommand $r\n")
            r
        }
    }

    private fun getNextTurn(): String {
        if (goHomeRequired) {
            f.appendText("GetNextTurn: home required: $goHomeRequired\n")
            val nearest = getNearestMyTerritoryPoint()
            f.appendText("GetNextTurn: nearest: $nearest\n")

            if (!::lastKnownDirection.isInitialized || !lastKnownDirection.isOppositeTo(nearest.direction)) {
                lastKnownDirection = nearest.direction
                return lastKnownDirection.toString()
            }

        }

        val dir = getNearestBorderDirection()
        f.appendText("GetNextTurn: nearestBorder: $dir\n")

        if (dir.steps > 1) {
            goHomeRequired = false
            if (!::lastKnownDirection.isInitialized || !lastKnownDirection.isOppositeTo(dir.direction)) {
                lastKnownDirection = dir.direction

                return lastKnownDirection.toString()
            }
        }

        return if (lastKnownDirection == Direction.up || lastKnownDirection == Direction.down) {
            goHomeRequired = true
            getRandom(commands.copyOfRange(0, 1))
        } else {
            goHomeRequired = true
            getRandom(commands.copyOfRange(2, 3))
        }

    }

    private fun getNearestBorderDirection(): NearestVertex {
        with(currentTick.me.position) {
            if (x >= y) {
                if (x > config.yCellsCount * config.squareWith - y) {
                    return NearestVertex(
                            Vertex(x, config.yCellsCount * config.squareWith),
                            (config.yCellsCount *
                                    config.squareWith - y) / config.defaultSpeed,
                            Direction.up)
                }
                return NearestVertex(Vertex(0, y), x / config.defaultSpeed, Direction.left)
            } else {
                if (config.xCellsCount * config.squareWith - x > y) {
                    return NearestVertex(Vertex(x, 0), y / config.defaultSpeed, Direction.down)
                }
                return NearestVertex(Vertex(config.xCellsCount * config.squareWith, y), (config.xCellsCount * config
                        .squareWith - x) / config.defaultSpeed, Direction.right)
            }
        }
    }

    private fun getRandom(commands: Array<String>): String {
        val rnd = Random().nextInt(commands.size)
        return commands[rnd]
    }

}

data class NearestVertex(val vertex: Vertex, val steps: Int, val direction: Direction)

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

enum class Direction {
    left, up, right, down;


    fun isOppositeTo(test: Direction): Boolean {
        return test == left && this == right ||
                test == right && this == left ||
                this == up && test == down ||
                this == down && test == up
    }

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