package just4fun.modularity.core.test.testEndurance

import just4fun.kotlinkit.measuredStats
import just4fun.modularity.core.BaseModule
import just4fun.modularity.core.test.*
import org.junit.Test
import java.util.concurrent.ScheduledThreadPoolExecutor
import kotlin.concurrent.thread
import kotlin.reflect.full.isSubclassOf
import java.lang.System.currentTimeMillis as now
import java.lang.Thread.currentThread as cThread
import java.util.concurrent.TimeUnit.MILLISECONDS as ms


class Test {
	@Test fun run() {
		main(emptyArray())
	}
}

fun main(args: Array<String>) {
	val sys = TContainer()
	sys.handle(SystemInitEvent())
	profileMemo()
	debug = 1
	durationSec = 60 * 60 * 5
}

val modulesSize = 10
val memLock = Any()
val scheduler = ScheduledThreadPoolExecutor(1).apply { maximumPoolSize = 4 }
val packageName = "just4fun.modularity.core.test.testEndurance"
val moduleRefs = Array<TModuleRef>(modulesSize, ::TModuleRef)
val activeModules = mutableListOf<TModule>()

fun ScheduledThreadPoolExecutor.schedule(delay: Int, code: () -> Unit) = schedule(code, delay.toLong(), ms)

fun moduleClass(id: Int): Class<TModule> = Class.forName("$packageName.M$id") as Class<TModule>

fun profileMemo() = thread {
	val runtime = Runtime.getRuntime()
	val MEGABYTE = 1024L * 1024L
	while (now() < deadline) {
		runtime.gc()
		Thread.sleep(100)
		val memory = runtime.totalMemory() - runtime.freeMemory()
		println("used memo= ${memory / MEGABYTE}")
		synchronized(memLock) { (memLock as java.lang.Object).wait(30000) }
	}
}


class M0: TModule()
class M1: TModule()
class M2: TModule()
class M3: TModule()
class M4: TModule()
class M5: TModule()
class M6: TModule()
class M7: TModule()
class M8: TModule()
class M9: TModule()
