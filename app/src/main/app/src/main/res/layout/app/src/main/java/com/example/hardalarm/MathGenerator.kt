package com.example.hardalarm

import kotlin.random.Random

object MathGenerator {

    data class Problem(val question: String, val options: List<String>, val correctIndex: Int)

    private data class Entry(val sin: String, val num: Int, val den: Int)

    private val entries = listOf(
        Entry("0", 0, 1),
        Entry("1/2", 1, 6),
        Entry("√2/2", 1, 4),
        Entry("√3/2", 1, 3),
        Entry("1", 1, 2)
    )

    private data class SinX(val x: String, val num: Int, val den: Int)
    private val sinX = listOf(
        SinX("2π/3", 1, 3), SinX("3π/4", 1, 4), SinX("5π/6", 1, 6),
        SinX("-5π/6", -1, 6), SinX("-3π/4", -1, 4), SinX("-2π/3", -1, 3)
    )

    fun generate(count: Int): List<Problem> {
        val builders = listOf(::typeDirect, ::typeArcsinSin, ::typeSolve, ::typeK, ::typeSum)
        val out = ArrayList<Problem>(count)
        val seen = HashSet<String>()
        var guard = 0
        while (out.size < count && guard++ < 100) {
            val p = builders[Random.nextInt(builders.size)]()
            if (p.question !in seen) {
                seen += p.question
                out += p
            }
        }
        return out
    }

    private fun typeDirect(): Problem {
        val e = entries[1 + Random.nextInt(entries.size - 1)]
        val neg = Random.nextBoolean()
        val q = "arcsin(${if (neg) "-" else ""}${e.sin}) = ?"
        val correct = fracLabel(if (neg) -e.num else e.num, e.den)
        return options(q, correct, entries.map { fracLabel(if (neg) -it.num else it.num, it.den) })
    }

    private fun typeArcsinSin(): Problem {
        val s = sinX[Random.nextInt(sinX.size)]
        val q = "arcsin(sin ${s.x}) = ?"
        val correct = fracLabel(s.num, s.den)
        val candidates = sinX.map { fracLabel(it.num, it.den) } + sinX.map { it.x.removePrefix("-") }
        return options(q, correct, candidates)
    }

    private fun typeSolve(): Problem {
        val e = entries[1 + Random.nextInt(entries.size - 1)]
        val neg = Random.nextBoolean()
        val q = "arcsin(x) = ${fracLabel(if (neg) -e.num else e.num, e.den)}  →  x = ?"
        val correct = if (neg) "-" + e.sin else e.sin
        val candidates = (entries.map { it.sin } + entries.map { "-" + it.sin }).filter { it != "0" && it != "-0" }
        return options(q, correct, candidates)
    }

    private fun typeK(): Problem {
        val e = entries[1 + Random.nextInt(entries.size - 1)]
        val k = 2 + Random.nextInt(2)
        val q = "$k · arcsin(${e.sin}) = ?"
        val correct = fracLabel(k * e.num, e.den)
        return options(q, correct, entries.filter { it !== e }.map { fracLabel(k * it.num, it.den) })
    }

    private fun typeSum(): Problem {
        val a = entries[1 + Random.nextInt(entries.size - 1)]
        val b = entries[1 + Random.nextInt(entries.size - 1)]
        val q = "arcsin(${a.sin}) + arcsin(${b.sin}) = ?"
        val num = a.num * b.den + b.num * a.den
        val den = a.den * b.den
        val correct = fracLabel(num, den)
        val candidates = listOf(
            fracLabel(num + 1, den), fracLabel(num - 1, den),
            fracLabel(-num, den), fracLabel(num, den * 2)
        )
        return options(q, correct, candidates)
    }

    private fun options(q: String, correct: String, candidates: List<String>): Problem {
        val wrong = candidates.filter { it != correct }.distinct().shuffled().take(3)
        val opts = (wrong + correct).shuffled()
        return Problem(q, opts, opts.indexOf(correct))
    }

    private fun fracLabel(num: Int, den: Int): String {
        if (num == 0) return "0"
        val g = gcd(kotlin.math.abs(num), den)
        val n = kotlin.math.abs(num / g)
        val d = den / g
        val sign = if (num < 0) "-" else ""
        val piPart = if (n == 1) "π" else "$nπ"
        return if (d == 1) sign + piPart else "$sign$piPart/$d"
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
