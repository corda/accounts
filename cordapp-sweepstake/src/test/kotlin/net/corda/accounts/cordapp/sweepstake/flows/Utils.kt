package net.corda.accounts.cordapp.sweepstake.flows

import net.corda.core.crypto.random63BitValue
import org.junit.Test
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.toList

class Utils {

companion object {
    val JAPAN: String = "Japan"
    val BELGIUM: String = "Belgium"
    }

    @Test
    fun`blah`(){
        val lon = random63BitValue()
        val str = random63BitValue().toString()
        val chrs = str.chars()
        val list = chrs.toList()
        val shuffled = list.shuffled()
        val intArray = shuffled.toIntArray()
        println("long: $lon")
        println("string: $str")
        println("chars: $chrs}")
        println("toList: $list")
        println("shuffled: $shuffled")
        println("toIntArray: $intArray")

        val score = str.chars().toList().shuffled().toIntArray()[str.length/2]
        println(score)
    }
//    private fun divide(length: String) : Int {
//        val n = length.length/2
//        while (n >= 0 && n < 10) {
//            return divide(n.toString())
//        }
//    }

    @Test
    fun `hey`() {
        val map = mapOf("hey" to 0, "heya" to 0)
        val result = score(map)
        println(result.values.first())
        println(result.values.last())
        }
    }

    private fun score(map: Map<String, Int>) : Map<String,Int> {
        val scores = map.mapValues { it.value.plus(ThreadLocalRandom.current().nextInt(0, 10)) }
        return if (scores.values.first() == scores.values.last()) {
           score(scores)
        } else {
            scores
        }
}
