import java.io.File
import java.util.*


fun getRandom(commands: Array<String>): String {
    val rnd = Random().nextInt(commands.size)
    return commands[rnd]
}

val f = File("test1")

fun main(args: Array<String>) {
    val commands = arrayOf("left", "right", "up", "down")
    f.appendText("1\n")
    while (true) {
        val input = readLine()
        f.appendText("$input\n")
        val command = getRandom(commands)
        println("{\"command\": \"$command\"}")
        f.appendText("$command\n")
    }
}