package my.ktbot.plugs

import my.ktbot.annotation.AutoCall
import my.ktbot.annotation.MsgLength
import my.ktbot.annotation.RegexAnn
import my.ktbot.interfaces.Plug
import my.ktbot.utils.CacheMap
import my.ktbot.utils.DiceResult
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.toPlainText

object CQBotSBI : Plug(
	name = "骰子：SBI特化功能",
	regex = Regex("^[.．。]s +(?<num>\\d*)d(?<max>\\d*)", RegexOption.IGNORE_CASE),
	weight = 1.1,
	help = "SBI骰子主功能".toPlainText(),
	msgLength = 4..500,
) {

	@JvmStatic
	val cache = CacheMap<Long, DiceResult>()

	@JvmStatic
	val cheater: Boolean get() = CQBotCOC.cheater

	override suspend fun invoke(event: MessageEvent, result: MatchResult): Message? {
		val num = (result["num"]?.value?.toIntOrNull() ?: return null).coerceAtLeast(3)
		val max = result["max"]?.value?.toIntOrNull() ?: return null
		val diceResult = when (cheater) {
			true -> DiceResult(num, max)
			false -> DiceResult.dice(num, max)
		}
		cache[event.sender.id] = diceResult
		return "${diceResult.origin}：[${diceResult.list.joinToString()}]（${getRes(diceResult.list)}）".toPlainText()
	}

	fun getRes(list: IntArray): String {
		if (list.size < 3) return "数量过少"
		setOf(list[0], list[1], list[2]).sorted().apply {
			if (size == 1) {
				return "大失败"
			}
			if (size == 3 && sum() == 6) {
				return "大成功，成功度${list.count(1::equals)}"
			}
		}
		val intArray = list.toSortedSet().toIntArray()
		val arr = intArrayOf(intArray[0], 0)
		for (i in intArray) {
			if (i - arr[0] == 1) {
				if (arr[1] == 1) return "成功，成功度${list.count(1::equals)}"
				else arr[1] = 1
			}
			else arr[1] = 0
			arr[0] = i
		}
		return "失败"
	}

	@AutoCall(
		name = "骰子：SBI加骰",
		regex = RegexAnn("^[.．。]sp(?<num> ?\\d*)", RegexOption.IGNORE_CASE),
		weight = 1.13,
		help = "10分钟之内加投骰",
		msgLength = MsgLength(3, 500)
	)
	private fun addedDice(event: MessageEvent, result: MatchResult): Message {
		val num = result["num"]?.run { value.trim().toIntOrNull() } ?: 1
		var diceResult: DiceResult = cache[event.sender.id] ?: return "10分钟之内没有投任何骰子".toPlainText()
		val dice: DiceResult = when (cheater) {
			true -> DiceResult(num, diceResult.max)
			false -> DiceResult.dice(num, diceResult.max)
		}
		diceResult += dice
		cache[event.sender.id] = diceResult
		return """${dice.origin}：[${dice.list.joinToString(", ")}]=${dice.sum}
			|[${diceResult.list.joinToString(", ")}]（${getRes(diceResult.list)}）
		""".trimMargin().toPlainText()
	}
}
