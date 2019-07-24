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

class SimpleStrategy(private val config: WorldConfig) {

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

        val dir = if (abs(currentTick.myX - target.x) > abs(currentTick.myY - target.y)) {
            if (currentTick.myX > target.x) {
                Direction.LEFT
            } else {
                Direction.RIGHT
            }
        } else {
            if (currentTick.myY > target.y) {
                Direction.DOWN
            } else {
                Direction.UP
            }
        }

        return NearestVertex(
                target,
                abs(target.y - currentTick.myY) / WorldConfig.STEP_LENGTH +
                        abs(target.x - currentTick.myX) / WorldConfig.STEP_LENGTH,
                dir
        )
    }


    fun getCommand(): String {
        return if (currentTick.tickNum == 1) {
            val r = Direction.LEFT.literal
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

            goHomeRequired = nearest.steps > 0

            if (nearest.steps > 0 &&
                    (!::lastKnownDirection.isInitialized ||
                            !lastKnownDirection.isOppositeTo(nearest.direction))) {
                lastKnownDirection = nearest.direction
                return lastKnownDirection.literal
            }

        }

        val dir = getNearestBorderDirection()
        f.appendText("GetNextTurn: nearestBorder: $dir\n")

        if (dir.steps > 0) {
            goHomeRequired = false
            if (!::lastKnownDirection.isInitialized || !lastKnownDirection.isOppositeTo(dir.direction)) {
                lastKnownDirection = dir.direction

                return lastKnownDirection.literal
            }
        }

        goHomeRequired = true
        val nearest = getNearestMyTerritoryPoint()
        if (nearest.steps > 0) {

            if (!lastKnownDirection.isOppositeTo(nearest.direction)) {
                lastKnownDirection = nearest.direction
                return lastKnownDirection.literal
            }

            if (lastKnownDirection.vertical) {
                if (currentTick.me.territory.vertexes.any { it.x > nearest.vertex.x }) {
                    lastKnownDirection = Direction.RIGHT
                    return lastKnownDirection.literal
                }
                if (currentTick.me.territory.vertexes.any { it.x < nearest.vertex.x }) {
                    lastKnownDirection = Direction.LEFT
                    return lastKnownDirection.literal
                }
            }

            if (lastKnownDirection.horizontal) {
                if (currentTick.me.territory.vertexes.any { it.y > nearest.vertex.y }) {
                    lastKnownDirection = Direction.UP
                    return lastKnownDirection.literal
                }
                if (currentTick.me.territory.vertexes.any { it.y < nearest.vertex.y }) {
                    lastKnownDirection = Direction.DOWN
                    return lastKnownDirection.literal
                }
            }

        }

        if (lastKnownDirection.vertical) {
            goHomeRequired = true
            lastKnownDirection = Direction.LEFT
        } else {
            goHomeRequired = true
            lastKnownDirection = Direction.UP
        }
        return lastKnownDirection.literal
    }

    private fun getNearestBorderDirection(): NearestVertex {
        with(currentTick.me.position) {

            if (x == config.minX || x == config.maxX) {
                return if (y > config.maxY - y) {
                    NearestVertex(
                            Vertex(x, config.maxY),
                            (config.maxY - y) / WorldConfig.STEP_LENGTH,
                            Direction.UP
                    )
                } else {
                    NearestVertex(
                            Vertex(x, config.minY),
                            (config.minY - y) / WorldConfig.STEP_LENGTH,
                            Direction.DOWN
                    )
                }
            }

            if (y == config.minY || y == config.maxY) {
                return if (x > config.maxX - x) {
                    NearestVertex(
                            Vertex(config.maxX, y),
                            (config.maxX - x) / WorldConfig.STEP_LENGTH,
                            Direction.RIGHT
                    )
                } else {
                    NearestVertex(
                            Vertex(config.minX, y),
                            (config.minX - x) / WorldConfig.STEP_LENGTH,
                            Direction.LEFT
                    )
                }
            }

            if (x < y && x > config.minX) {
                if (x > config.maxY - y && y < config.maxY) {
                    return NearestVertex(
                            Vertex(x, config.maxY),
                            (config.maxY - y) / WorldConfig.STEP_LENGTH,
                            Direction.UP
                    )
                }
                return NearestVertex(Vertex(config.minX, y), x / WorldConfig.STEP_LENGTH, Direction.LEFT)
            } else {
                if (config.maxX - x > y && y > config.minY) {
                    return NearestVertex(Vertex(x, config.minY), y / WorldConfig.STEP_LENGTH, Direction.DOWN)
                }

                return NearestVertex(
                        Vertex(config.maxX, y), (config.maxX - x) / WorldConfig.STEP_LENGTH, Direction.RIGHT
                )

            }

        }
    }

//    private fun getRandom(commands: Array<String>): String {
//        val rnd = Random().nextInt(commands.size)
//        return commands[rnd]
//    }

}

data class NearestVertex(val vertex: Vertex, val steps: Int, val direction: Direction)

data class TickData(
        val enemies: ArrayList<PlayerTickData>,
        val me: PlayerTickData,
        val tickNum: Int,
        val bonuses: BonusesList
) {

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

            return TickData(
                    en, me,
                    jsTick.getJSONObject("params").getInt("tick_num"),
                    BonusesList.build(jsTick.getJSONObject("params").getJSONArray("bonuses"))
            )
        }
    }

    val myX
        get() = me.position.x
    val myY
        get() = me.position.y
}

data class PlayerTickData(
        val name: String,
        val score: Int,
        val direction: Direction,
        val territory: VertexList,
        val lines: VertexList,
        val position: Vertex,
        val bonuses: BonusesList
) {
    companion object {
        fun build(name: String, tickData: JSONObject): PlayerTickData {
            val direction = when (tickData.optString("direction")) {
                Direction.DOWN.literal -> Direction.DOWN
                Direction.LEFT.literal -> Direction.LEFT
                Direction.RIGHT.literal -> Direction.RIGHT
                Direction.UP.literal -> Direction.UP
                else -> Direction.NONE
            }
            return PlayerTickData(
                    name,
                    tickData.getInt("score"),
                    direction,
                    VertexList.build(tickData.getJSONArray("territory")),
                    VertexList.build(tickData.getJSONArray("lines")),
                    Vertex.build(tickData.getJSONArray("position")),
                    BonusesList.build(tickData.getJSONArray("bonuses"))
            )
        }
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

data class Bonus(
        val type: BonusType,
        var ticks: Int,
        var position: Vertex
) {
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

enum class Direction(val intDir: Int, val literal: String) {


    LEFT(1, "left"),
    UP(2, "up"),
    RIGHT(3, "right"),
    DOWN(4, "down"),
    NONE(100, "null");


    fun isOppositeTo(test: Direction): Boolean {
        return abs(test.intDir - this.intDir) == 2
    }

    val vertical: Boolean
        get() = this == UP || this == DOWN

    val horizontal: Boolean
        get() = this == LEFT || this == RIGHT

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

data class WorldConfig(
        val xCellsCount: Int,
        val yCellsCount: Int,
        val defaultSpeed: Int,
        val squareWith: Int
) {
    companion object {

        const val STEP_LENGTH = 30

        fun build(firstTickData: JSONObject): WorldConfig? {
            if (firstTickData.getString("type") != "start_game") {
                return null
            }
            val configJson = firstTickData.getJSONObject("params")
            return WorldConfig(
                    configJson.getInt("x_cells_count"),
                    configJson.getInt("y_cells_count"),
                    configJson.getInt("speed"),
                    configJson.getInt("width")
            )
        }
    }

    val mapWidth = xCellsCount * squareWith
    val mapHeight = yCellsCount * squareWith
    val maxX = mapWidth - STEP_LENGTH / 2
    val maxY = mapHeight - STEP_LENGTH / 2
    val minX = STEP_LENGTH / 2
    val minY = STEP_LENGTH / 2


    override fun toString(): String {
        return "xCells: $xCellsCount, yCells: $yCellsCount, defaultSpeed: $defaultSpeed, width: $squareWith; Nested: map $mapWidth x $mapHeight"
    }
}