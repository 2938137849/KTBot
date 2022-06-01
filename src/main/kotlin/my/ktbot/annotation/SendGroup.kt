package my.ktbot.annotation

import my.ktbot.utils.toMessage
import my.miraiplus.Caller
import my.miraiplus.injector.Injector
import net.mamoe.mirai.event.events.GroupEvent
import net.mamoe.mirai.message.data.isContentBlank
import kotlin.reflect.KClass

/**
 *  @Date:2022/5/31
 *  @author bin
 *  @version 1.0.0
 */
annotation class SendGroup {
	object Inject : Injector<SendGroup, GroupEvent> {
		override val event: KClass<GroupEvent> = GroupEvent::class
		override suspend fun doAfter(ann: SendGroup, event: GroupEvent, caller: Caller, result: Any?) {
			val message = result.toMessage()
			if (message === null || message.isContentBlank()) {
				return
			}
			event.intercept()
			event.group.sendMessage(message)
		}
	}
}