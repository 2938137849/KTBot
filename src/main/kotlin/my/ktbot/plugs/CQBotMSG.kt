package my.ktbot.plugs

import my.ktbot.interfaces.Plug
import my.ktbot.interfaces.SubPlugs
import my.ktbot.utils.ReplaceNode
import my.ktbot.utils.sendAdmin
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.MessageSource.Key.quote

/**
 *
 * @author bin
 * @since 1.0
 * @date 2022/1/14
 */
object CQBotMSG : SubPlugs {
	private val Ping = Plug.StringPlug(
		name = ".ping",
		regex = Regex("^[.．。]ping$", RegexOption.IGNORE_CASE),
		weight = 0.0,
		help = "测试bot是否连接正常".toPlainText(),
		msgLength = 4..6,
		msg = ".pong!".toPlainText()
	)

	private val Data = Plug.StringPlug(
		name = ".data",
		regex = Regex("^[.．。]data$", RegexOption.IGNORE_CASE),
		weight = 10.0,
		help = "开发者信息".toPlainText(),
		deleteMSG = 90 * 1000,
		msgLength = 4..6,
		msg = """
			|开发者QQ：2938137849
			|项目地址github：2938137849/KTBot
			|轮子github：mamoe/mirai
		""".trimMargin().toPlainText()
	)

	override val subPlugs: List<Plug> = listOf(
		Ping, Data,
		Report, MemeAI
	)

	private object Report : Plug(
		name = ".report <txt>",
		regex = Regex("^[.．。]report(?<txt>.+)$", RegexOption.IGNORE_CASE),
		weight = 6.0,
		help = "附上消息发送给开发者".toPlainText()
	) {
		override suspend fun invoke(event: MessageEvent, result: MatchResult): Message? {
			val txt = result["txt"]?.value ?: return null
			event.sendAdmin("来自 ${event.senderName}(${event.sender.id}):\n${txt}".toPlainText())
			return "收到".toPlainText()
		}
	}

	/**
	 *  @Date:2022/1/8
	 *  @author bin
	 *  @version 1.0.0
	 */
	object MemeAI : Plug(
		name = "(@复读AI)",
		regex = Regex("^"),
		weight = 91.0,
		msgLength = 0..50,
		hidden = true
	) {
		@JvmStatic
		private val replaceNode = ReplaceNode() + mapOf(
			"不" to "很",
			"你" to "我",
			"我" to "你",
			"有" to "没有",
			"没有" to "有",
			"有没有" to "肯定有",
			"是" to "不是",
			"不是" to "是",
			"是不是" to "肯定是",
			"？" to "!",
			"?" to "!",
			"吗" to "",
		)

		init {
			canPrivate = false
		}

		override suspend fun invoke(event: GroupMessageEvent, result: MatchResult): Message? {
			if (!event.message.contains(At(event.bot.id))) return null
			val msg: String = replaceNode.replace(event.message.filterIsInstance<PlainText>()
				.joinToString("", transform = PlainText::contentToString))
			return buildMessageChain {
				+event.message.quote()
				+event.sender.at()
				+msg
			}
		}
	}
}
