package my.ktbot.plugs

import my.ktbot.database.COCShortKey
import my.ktbot.database.TCOCShortKey
import my.ktbot.interfaces.Plug
import my.ktbot.interfaces.SubPlugs
import my.ktbot.utils.CacheMap
import my.ktbot.utils.DiceResult
import my.ktbot.utils.Sqlite
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.toPlainText
import org.ktorm.dsl.eq
import org.ktorm.entity.add
import org.ktorm.entity.removeIf
import org.ktorm.entity.toList
import kotlin.text.RegexOption.IGNORE_CASE

/**
 *  @since:2022/1/2
 *  @author bin
 */
object CQBotCOC : Plug(
	name = "骰子主功能",
	regex = Regex("^[.．。]d +(?:(?<times>\\d)#)?(?<dice>[^ ]+)", IGNORE_CASE),
	weight = 1.1,
	help = "骰子主功能，附带简单表达式计算".toPlainText(),
	msgLength = 4..500
), SubPlugs {
	@JvmStatic
	private val diceRegex = Regex("[^+\\-*d\\d#]", IGNORE_CASE)

	@JvmStatic
	val cache = CacheMap<Long, DiceResult>()

	@JvmStatic
	var cheater: Boolean = false
	override suspend fun invoke(event: MessageEvent, result: MatchResult): Message? {
		val times: Int = result["times"]?.run { value.trim().toIntOrNull() } ?: 1
		var dice: String = result["dice"]?.value ?: return null

		for (sk in Sqlite[TCOCShortKey]) {
			dice = dice.replace(sk.key, sk.value, true)
		}

		if (diceRegex.matches(dice)) {
			return ".d错误参数".toPlainText()
		}
		val str = Array(times) { dice(dice, event.sender.id) }.joinToString("\n")
		return str.toPlainText()
	}

	@JvmStatic
	private val splitDiceRegex = Regex("(?=[+\\-*])")

	@JvmStatic
	private fun dice(str: String, qq: Long): String {
		val handles = splitDiceRegex.split(str).map {
			castString(it, this.cheater)
		}
		if (handles.size == 1) {
			val calc: Calc = handles[0]
			return if (calc.list === null) "${calc.op}${calc.origin}=${calc.sum}"
			else {
				this.cache[qq] = DiceResult(calc.sum, calc.list, calc.max)
				specialEffects(calc)
				"${calc.origin}：[${calc.list.joinToString()}]=${calc.sum}${calc.state}"
			}
		}
		val preRet: String = handles.filter {
			it.list !== null
		}.joinToString(separator = "\n") {
			"${it.origin}：[${it.list!!.joinToString()}]=${it.sum}"
		}
		val s = handles.joinToString("") { "${it.op}${it.origin}" }
		return "${preRet}\n${s}=${handles.calculate()}"
	}

	@JvmStatic
	private fun List<Calc>.calculate(): Long {
		return foldRight(0L to 1L) { c, arr -> c.op.func(arr, c.sum) }.first
	}

	@JvmStatic
	private val castStringRegex = Regex("^(?<op>[+\\-*])?(?<num>\\d+)?(?:d(?<max>\\d+))?$", IGNORE_CASE)

	@JvmStatic
	private fun castString(origin: String, cheater: Boolean): Calc {
		val result =
			castStringRegex.matchEntire(origin) ?: return Calc(op = Operator.Add, sum = 0, origin = origin, max = 0)
		val num: Int = result["num"]?.run { value.toIntOrNull() } ?: 1
		val op = when (result["op"]?.value) {
			"+" -> Operator.Add
			"-" -> Operator.Sub
			"*" -> Operator.Mul
			else -> Operator.Add
		}
		val max = result["max"]?.run { value.toIntOrNull() } ?: return Calc(op = op,
			sum = num.toLong(),
			origin = num.toString(),
			max = 0)
		val dices: DiceResult = when (cheater) {
			true -> DiceResult(num, max)
			false -> DiceResult.dice(num, max)
		}
		return Calc(op = op, sum = dices.sum, list = dices.list, max = dices.max, origin = dices.origin)
	}

	class Calc(
		val op: Operator,
		val sum: Long,
		val list: IntArray? = null,
		val origin: String,
		val max: Int,
	) {
		var state: String = ""
			set(v) {
				field = if (v == "") "" else "\n$v"
			}
	}

	enum class Operator(private val s: String, val func: (Pair<Long, Long>, Long) -> Pair<Long, Long>) {
		Add("+", { (first, second), num ->
			(first + num * second) to 1
		}),
		Sub("-", { (first, second), num ->
			(first - num * second) to 1
		}),
		Mul("*", { (first, second), num ->
			first to (second * num)
		});

		override fun toString(): String = s
	}

	@JvmStatic
	var specialEffects: Effects = Effects.bug

	@Suppress("EnumEntryName", "unused")
	enum class Effects(val state: String) : (Calc) -> Unit {
		bug("默认") {
			override fun invoke(calc: Calc) {}
		},
		wrf("温柔f") {
			override fun invoke(calc: Calc) {
				calc.list?.also {
					if (it.size > 2 && it[0] == it[1]) {
						++it[1]
						calc.state = "[温柔]"
					}
				}
			}
		},
		cbf("残暴f") {
			override fun invoke(calc: Calc) {
				calc.list?.also {
					if (it.size > 2) {
						it[1] = it[0]
						calc.state = "[残暴]"
					}
				}
			}
		},
		ajf("傲娇f") {
			override fun invoke(calc: Calc) = if (Math.random() < 0.5) wrf(calc) else cbf(calc)
		},
		wr("温柔") {
			override fun invoke(calc: Calc) = if (Math.random() < 0.5) wrf(calc) else bug(calc)
		},
		cb("残暴") {
			override fun invoke(calc: Calc) = if (Math.random() < 0.5) cbf(calc) else bug(calc)
		},
		aj("傲娇") {
			override fun invoke(calc: Calc) = arrayOf(wrf, cbf, bug).random()(calc)
		},
		;
	}

	override val subPlugs: List<Plug> = listOf(COCCheater, COCStat, COCStatSet, COCAdded, COCSpecial)

	/**
	 *
	 * @author bin
	 * @since 2022/1/7
	 */
	object COCCheater : Plug(
		name = "骰子:打开全1模式",
		regex = Regex("^[.．。]dall1$", IGNORE_CASE),
		weight = 1.11,
		msgLength = 5..10,
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message {
			cheater = !cheater
			return ("全1" + (if (cheater) "开" else "关")).toPlainText()
		}
	}

	/**
	 *
	 * @author bin
	 * @since 2022/1/7
	 */
	object COCStat : Plug(
		name = "骰子：简写",
		regex = Regex("^[.．。]dstat$", IGNORE_CASE),
		weight = 1.01,
		help = "查看全部简写".toPlainText(),
		msgLength = 5..7,
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message {
			val list = Sqlite[TCOCShortKey].toList()
			return (if (list.isEmpty()) "空"
			else list.joinToString("\n") { sk ->
				"${sk.key}=${sk.value}"
			}).toPlainText()
		}
	}

	/**
	 *
	 * @author bin
	 * @since 2022/1/7
	 */
	object COCStatSet : Plug(
		name = "骰子：删除[设置]简写",
		regex = Regex("^[.．。]dset +(?<key>\\w[\\w\\d]+)(?:=(?<value>[+\\-*d\\d#]+))?", IGNORE_CASE),
		weight = 1.02,
		help = "删除[设置]简写".toPlainText(),
		msgLength = 5..100,
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message {
			val key = result["key"]?.value
			val value = result["value"]?.value
			if (key === null || key.length < 2) {
				return "key格式错误或长度小于2".toPlainText()
			}
			val shortKey = Sqlite[TCOCShortKey]
			if (value === null) {
				shortKey.removeIf { it.key eq key }
				return "删除key:${key}".toPlainText()
			}
			if (value.length > 10) {
				return "value长度不大于10".toPlainText()
			}
			shortKey.add(COCShortKey {
				this.key = key
				this.value = value
			})
			return "添加key:${key}=${value}".toPlainText()
		}
	}

	/**
	 *
	 * @author bin
	 * @since 2022/1/7
	 */
	object COCAdded : Plug(
		name = "骰子：加骰",
		regex = Regex("^[.．。]dp(?<num> ?\\d*)", IGNORE_CASE),
		weight = 1.13,
		help = "10分钟之内加投骰".toPlainText(),
		msgLength = 3..10
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message {
			val num = result["num"]?.run { value.trim().toIntOrNull() } ?: 1
			var cache: DiceResult = CQBotCOC.cache[event.sender.id] ?: return "10分钟之内没有投任何骰子".toPlainText()
			val dice: DiceResult = when (cheater) {
				true -> DiceResult(num, cache.max)
				false -> DiceResult.dice(num, cache.max)
			}
			cache += dice
			CQBotCOC.cache[event.sender.id] = cache
			return """${dice.origin}：[${dice.list.joinToString(", ")}]=${dice.sum}
			|[${cache.list.joinToString(", ")}]
		""".trimMargin().toPlainText()
		}
	}

	/**
	 *
	 * @author bin
	 * @since 2022/1/7
	 */
	object COCSpecial : Plug(
		name = "骰子：特殊模式",
		regex = Regex("^[.．。]d(?<operator>bug|(?:wr|cb|aj)f?)$", IGNORE_CASE),
		weight = 1.12,
		help = "打开/关闭特殊模式".toPlainText(),
		msgLength = 2..10,
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message? {
			val operator = result["operator"]?.value ?: return null
			if (operator == "bug") {
				specialEffects = Effects.bug
				return "进入默认状态".toPlainText()
			}
			return try {
				specialEffects = Effects.valueOf(operator)
				"进入${specialEffects.state}状态".toPlainText()
			} catch (e: Exception) {
				"未知状态".toPlainText()
			}
		}
	}
}
