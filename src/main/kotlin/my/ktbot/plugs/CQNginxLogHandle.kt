package my.ktbot.plugs

import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import my.ktbot.PlugConfig
import my.ktbot.annotation.AutoCall
import my.ktbot.annotation.RegexAnn
import my.ktbot.interfaces.Plug
import my.ktbot.utils.KtorUtils.json
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.toPlainText
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CQNginxLogHandle : Plug(
	name = "nginx日志处理",
	regex = Regex("^[.．。]nginx$", RegexOption.IGNORE_CASE),
	weight = 10.0,
	needAdmin = true,
	canPrivate = false,
	canGroup = true
) {
	private val formater = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH-mm'.yml'")
	private val nowFile: String get() = formater.format(LocalDateTime.now())
	override suspend fun invoke(event: MessageEvent, result: MatchResult): Message {
		val logToYml = logToYml()
		if (logToYml.size < 10) {
			return "没有日志".toPlainText()
		}
		val resource = logToYml.toExternalResource(".yml").toAutoCloseable()
		val group = PlugConfig.getAdminGroup(event.bot)
		group.launch {
			group.files.uploadNewFile(nowFile, resource)
			flushFiles()
		}
		return "正在发送".toPlainText()
	}

	private val ipRegex =
		Regex("((\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])\\.){3}(\\d|[1-9]\\d|1\\d\\d|2[0-4]\\d|25[0-5])")

	@AutoCall(
		name = "添加banip",
		regex = RegexAnn("^[.．。]banip (.+)$", RegexOption.IGNORE_CASE),
		weight = 10.0,
		needAdmin = true
	)
	private fun addIP(result: MatchResult): String {
		val s = result.groups[1]?.value ?: return "未匹配到ip"
		val list = ipRegex.findAll(s).map(MatchResult::value).toMutableList()
		if (list.isEmpty()) {
			return "未匹配到ip"
		}
		return list.joinToString("\n") {
			try {
				val start = ProcessBuilder("ipset", "add", "banip", it).start()
				start.waitFor()
				it + " -> " + start.errorReader().readLine()
			} catch (e: Exception) {
				logger.error(e)
				"执行出错：$it"
			}
		}
	}

	private val nginxRegex = Regex(
		"^(?<ip>[^ ]+) - (?<user>[^ ]+) \\[(?<time>[^]]+)] \"(?:(?<method>[A-Z]{3,7}) )?(?<url>[^ ]*)(?: (?<protocol>[^\"]+))?\" (?<status>\\d+) (?<bytes>\\d+) \"(?<referer>[^\"]*)\" \"(?<agent>[^\"]*)\"",
	)

	@Serializable
	private data class LogNginx(
		var fromFile: String? = null, // access80.log
		val user: String, // -
		val time: String, // 26/Mar/2022:08:17:25 +0000
		val status: Int, // 404
		val referer: String, // -
		val bytes: Int, // 153
		val method: String?, // GET
		val url: String, // /
		val protocol: String?, // HTTP/1.1
		val agent: String, // -
	) {
		constructor(result: MatchResult) : this(
			user = result["user"]!!.value,
			time = result["time"]!!.value,
			status = result["status"]!!.value.toInt(),
			referer = result["referer"]!!.value,
			bytes = result["bytes"]!!.value.toInt(),
			method = result["method"]?.value,
			url = result["url"]!!.value,
			protocol = result["protocol"]?.value,
			agent = result["agent"]!!.value,
		)

		fun test(): Boolean {
			if (status / 100 == 2) {
				return false
			}
			return true
		}
	}

	private fun logToYml(): ByteArray {
		val map = HashMap<String, MutableList<LogNginx>>()
		for (file in getConfFileList(PlugConfig.nginxLogPath)) {
			val name = file.name
			logger.info("Read File: $name")
			BufferedReader(FileReader(file)).use {
				for (line in it.lines()) {
					val result = nginxRegex.find(line, 0)
					if (result == null) {
						logger.info("\tNot Match Line: $line")
						continue
					}
					val element = LogNginx(result)
					element.fromFile = name
					if (element.test()) {
						map.computeIfAbsent(result["ip"]!!.value) { ArrayList() }.add(element)
					}
				}
			}
		}
		val it = StringBuilder()
		for ((ip, list) in map.toList().sortedByDescending { it.second.size }) {
			it.append(ip)
			it.append(":\n")
			for (logNginx in list) {
				it.append("  - ")
				it.append(json.encodeToString(serializer(), logNginx))
				it.append("\n")
			}
		}
		return it.toString().toByteArray()
	}

	private var files: ArrayList<File> = ArrayList()

	/**
	 * 根据传入路径返回路径下全部文件，确保 <code>File.isFile() == true </code>
	 * @param path String
	 * @return List<File>
	 */
	private fun getConfFileList(path: String): List<File> {
		val pathFile = File(path)
		if (!pathFile.exists() || !pathFile.isDirectory) {
			return emptyList()
		}
		val list = (pathFile.listFiles() ?: emptyArray<File>()).filter(File::isFile)
		files.addAll(list)
		return list
	}

	private fun flushFiles() {
		while (files.size > 0) {
			val file = files.removeAt(files.size - 1)
			BufferedWriter(FileWriter(file, false)).use {
				it.newLine()
			}
		}
	}
}
