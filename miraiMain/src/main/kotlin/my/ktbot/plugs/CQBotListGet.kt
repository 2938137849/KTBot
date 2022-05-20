package my.ktbot.plugs

import my.ktbot.annotation.AutoCall
import my.ktbot.annotation.RegexAnn
import my.ktbot.database.TGroup
import my.ktbot.database.TMembers
import my.ktbot.interfaces.Plug
import my.ktbot.utils.Counter
import my.ktbot.utils.calculator.Calculator
import my.ktbot.utils.sqlite.Sqlite
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import org.ktorm.dsl.eq
import org.ktorm.entity.filter
import org.ktorm.entity.joinTo

/**
 *
 * @author bin
 * @since 1.0
 * @date 2022/1/13
 */
object CQBotListGet : Plug(
	name = ".获取<type>列表",
	regex = Regex("^[.．。]获取(?<type>[^ ]+)列表$"),
	weight = 4.0,
	needAdmin = true,
) {
	override suspend fun invoke(event: MessageEvent, result: MatchResult): Message? {
		val type = result["type"]?.value ?: return null
		when (type) {
			"群" -> return event.bot.groups.run {
				"总共 ${size} 个群:\n" + joinToString("\n") {
					"${it.id}: ${it.name}"
				}
			}.toPlainText()
			"好友" -> return event.bot.friends.run {
				"总共 ${size} 个好友:\n" + joinToString("\n") {
					"${it.id}: ${it.nick}"
				}
			}.toPlainText()
			"ban" -> return StringBuilder("群：\n").also { b ->
				Sqlite[TGroup].filter { it.isBaned eq true }.joinTo(b, "\n") { it.id.toString() }
			}.append("\n人：\n").also { b ->
				Sqlite[TMembers].filter { it.isBaned eq true }.joinTo(b, "\n") { it.id.toString() }
			}.toString().toPlainText()
			else -> return null
		}
	}

	@AutoCall(
		name = ".插件[<id>]",
		regex = RegexAnn("^[.．。]插件(?<id> *\\d*)$"),
		weight = 4.0,
		needAdmin = true,
		help = "查看插件信息"
	)
	@JvmStatic
	private fun cqBotPluginInfo(result: MatchResult): Message {
		val p = run {
			val id = result["id"]?.run { value.trim().toIntOrNull() } ?: return@run null
			plugs.getOrNull(id)
		} ?: return plugs.mapIndexed { i, p ->
			"$i (${p.isOpen}):${p.name}"
		}.joinToString("\n").toPlainText()
		return """
			|名称：${p.name}
			|匹配：${p.regex}
			|权重：${p.weight}
			|启用：${p.isOpen}
			|群聊：${p.canGroup}
			|私聊：${p.canPrivate}
			|帮助：${p.help}
			|长度限制：${p.msgLength}
			|撤回延时：${p.deleteMSG}毫秒
			|速度限制：${p.speedLimit}毫秒每次
		""".trimMargin().toPlainText()
	}

	@AutoCall(
		name = ".插件<open><nums[]>",
		regex = RegexAnn("^[.．。]插件(?<open>[开关])(?<nums>[\\d ]+)$"),
		weight = 4.0,
		needAdmin = true,
		help = "设置插件状态"
	)
	@JvmStatic
	private fun cqBotPluginStatus(result: MatchResult): Message? {
		val isOpen = when (result["open"]!!.value) {
			"开" -> true
			"关" -> false
			else -> return null
		}
		val ids = result["nums"]!!.value.split(" ").mapNotNull {
			it.trim().toIntOrNull()
		}.mapNotNull {
			plugs.getOrNull(it)
		}
		if (ids.isEmpty()) {
			return "未知插件ID".toPlainText()
		}
		return buildMessageChain {
			+"插件变动:"
			ids.forEach {
				it.isOpen = isOpen
				+"\n"
				+it.name
			}
		}
	}

	@AutoCall(
		name = "日志",
		regex = RegexAnn("^[.．。]日志$"),
		weight = 10.0,
		needAdmin = true,
	)
	@JvmStatic
	private fun cqBotCounter(event: MessageEvent): Message {
		return Counter.state(event.subject)
	}

	/**
	 *  @Date:2022/2/3
	 *  @author bin
	 *  @version 1.0.0
	 */
	@AutoCall(
		name = "简易计算器",
		regex = RegexAnn("^[.．。]calc (?<calc>[^ ]+)"),
		weight = 10.0,
		help = "表达式间不允许出现空格"
	)
	@JvmStatic
	private fun cqBotCalculate(result: MatchResult): Message {
		val calc = result["calc"]?.value ?: return EmptyMessageChain
		return try {
			"结果为${Calculator(calc).v}".toPlainText()
		} catch (e: Exception) {
			e.toString().toPlainText()
		}
	}
}