package just4fun.modularity.core.test.testEndurance

import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.ContainerState
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit.MILLISECONDS as ms
import just4fun.modularity.core.test.*


class TContainer: ModuleContainer() {
	val maxLifeTime = 20// sec
	private val id = "SYS"
	private var controller: Thread? = null
	private var eventsSentCount = AtomicInteger()
	private val eventHandler = eventChannel(TModule::onModuleEvent)
	var overtime = false
	
	init {
		startTime = now()
	}
	
	var deadLine = startTime + (maxLifeTime + 20) * 1000L
	var lifetime = 0L
	val timeLeft get() = (lifetime - now()).let { if (it <= 0) 0 else it }.toInt()
	
	fun handle(e: Any) {
		log(2, id, "$e ")
		when (e) {
			is SystemInitEvent -> {
				controller = thread {
					try {
						Thread.sleep(maxLifeTime * 1000L)
						overtime = true
						while (now() < deadLine && !Thread.currentThread().isInterrupted) {
							log(2, id, "Overtime   Container ;        Left: Regd: ${registered()};   Unregd: ${unregistered()}")
							log(2, id, registered(true) + "\n" + unregistered(true))
							Thread.sleep((maxLifeTime + 5) * 1000L)
						}
						if (!Thread.currentThread().isInterrupted) {
							log(2, id, "Exception Overtime   Killing Container ;        Left: Regd: ${registered()};   Unregd: ${unregistered()}")
							log(2, id, registered(true) + "\n" + unregistered(true))
							System.exit(55)
						}
					} catch (x: Throwable) {
					}
				}
				lifetime = 0
				val refs = Array(rnd1(2)) { val n = rnd0(modulesSize - 1); moduleRefs[n] }
				val restful = refs.fold(true) { pvs, cur -> pvs && cur.startRestful }
				refs.forEach { ref ->
					thread {
						val t = if (restful) 99 else rnd1(maxLifeTime) * 1000
						lifetime = StrictMath.max(t.toLong(), lifetime)
						log(2, id, "Launch:  M${ref.id}    lifetime= $t;  ${ref.dumpConfig}")
						val bond = moduleRef(ref.clas)
						bond.bind()
						scheduler.schedule(t) {
							log(2, id, "Stopping:  M${ref.id}:${ref.module?.triggers()};        Left: Regd: ${registered()};   Unregd: ${unregistered()}")
							bond.unbind()
						}
					}
				}
				lifetime += now()
			}
			is SystemStartEvent -> Unit
			is SystemStopEvent -> {
				controller?.interrupt()
				if (now() > deadline) shutdown()
				else scheduler.schedule(0) {
					overtime = false
					println("\n####\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n")
					moduleRefs.forEach { it.reset() }
					startTime = now()
					deadLine = startTime + (maxLifeTime + 20) * 1000L
					handle(SystemInitEvent())
				}
			}
			else -> {
			}
		}
	}
	
	fun sendEvent(): Unit {
		val n = eventsSentCount.incrementAndGet()
		val rid = "$id-$n"
		eventHandler(ModuleEvent(null, rid))
	}
	
	
	override fun onContainerPopulated() = handle(SystemStartEvent())
	override fun onContainerEmpty() = handle(SystemStopEvent())
	override fun debugState(value: ContainerState) = log(3, id, value.toString().toUpperCase())
	override fun logError(error: Throwable) {
		if (error.cause is IntendedException) log(1, id, "$error")
		else logE(1, id, "$error\n${error.stackTrace.take(4).joinToString("\n")}${error.cause?.let { "      Caused by $it\n${it.stackTrace.take(4).joinToString("\n")}" } ?: ""}")
	}
	
	/* utils */
	
	fun shutdown() = thread {
		synchronized(memLock) { (memLock as java.lang.Object).notify() }
		Thread.sleep(100)
		//		tiker.shutdown(100)// FIXME should not shutdown container's scheduler. it should do itself.in containerTryShutdown
		scheduler.shutdownNow()
		scheduler.awaitTermination(100, ms)
		log(2, id, "scheduler Terminated? ${scheduler.isTerminated} ")
		//		log(2, id, "tiker Terminated? ${tiker.isTerminated}")
	}
	
	
}



/* events */

class SystemInitEvent: SystemEvent()
class SystemStartEvent: SystemEvent()
class SystemStopEvent: SystemEvent()

open class SystemEvent {
	open fun details(): String = toString()
	override fun toString() = javaClass.simpleName.substringBefore("Event")
}
