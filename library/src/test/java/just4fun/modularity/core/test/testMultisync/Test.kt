//package just4fun.core.multisync
//
//import just4fun.core.multisync.Test.memo
//import just4fun.core.multisync.Test.profileMemo
//import java.util.*
//import kotlin.concurrent.thread
//import java.lang.System.currentTimeMillis as now
//
//fun main(args: Array<String>) {
//	for (t in Test.threads) t.start()
//		memo = profileMemo()
//}
//
//internal object Test {
//	var debugState = 1
//	val MINUTES = 1
//	val SECONDS = 62
//	val durationSec = MINUTES * SECONDS * 1000
//	val EN = 4
//	val TN = 5
//	val PN = 2
//	var DEPTH = 10
//	val maxPriority = 2
//	val entities = {
//		val list = mutableListOf<Synt>()
//		for (n in 0..(EN - 1)) list.add(Synt())
//		list
//	}()
//	val threads = {
//		val list = mutableListOf<Thread>()
//		for (n in 1..TN) list.add(Thread { loop() })
//		list
//	}()
//	internal val tids = kotlin.arrayOfNulls<ThreadInfo>(threads.size)
//	val rnd0 = Random()
//	var deadline = now() + durationSec
//	var counter = 0
//	var lastId = 0
//	var glitches = 0
//	var exiting = false
//	var memo: Thread? = null
//	internal val infos = mutableListOf<ThreadInfo>()
//	@JvmName("addInfo") @JvmStatic
//	@Synchronized internal fun addInfo(info: ThreadInfo) = infos.add(info)
//
//	internal fun exec(tid: ThreadInfo, from: String?): Unit {
//		val e = entities[rnd0.nextInt(entities.size)]
//		log(e, tid, tid.doneCount, ">", "", if (from != null) "from $from" else "")
//		synchronized(tid) { tid.oopsCount++ }
//		val code = {
//			if (rnd0.nextInt(DEPTH) > 0) {
//				val ms = if (from == null) "E${e.numId}" else "$from > E${e.numId}"
//				exec(tid, ms)
//			}
//		}
//		when (rnd0.nextInt(3)) {
//			0 -> run { e.lockedOrDiscard(rnd0.nextInt(maxPriority), code); synchronized(tid) { tid.oopsCount-- } }
//			1 -> run { e.lockedOrTamper(rnd0.nextInt(maxPriority), code); synchronized(tid) { tid.oopsCount-- } }
//			2 -> run { e.lockedOrDefer(code); synchronized(tid) { tid.oopsCount-- } }
//		}
//	}
//
//	internal fun loop() {
//		val tid = ThreadInfo.info.get()
//		tids[tid.id] = tid
//		while (deadline > now()) {
//			try {
//				exec(tid, null)
//			} catch (x: Throwable) {
//				println("Oops...                   " + x)
//				x.printStackTrace()
//				if (debugState == 0) abort(4, x.toString())
//			}
//		}
//		println("~# $tid  -------------------------------------------------------------------------------------")
//		exit()
//	}
//
//	internal fun exit() {
//		if (exiting) return
//		exiting = true
//		DEPTH = 1
//		debugState = 0
//		thread {
//			if (memo != null) synchronized(memo!!) { (memo as java.lang.Object).notify() }
//			val t0 = now()
//			while (infos.any { it.thread.isAlive }) {
//				println("~# FINISHING ${now() - t0} ms::  ${tids.map { "$it:${it?.thread?.isAlive}:[E${it?.waitOn?.numId}]; ${it?.doneCount};" }.joinToString(";   ")}")
//				println("Entities::   ${entities.map { e -> val list = e.toList()
//					"${e.locker}[${e.numId}];   total= ${e.total};    deferred[${e.deferred?.order}:${list.getOrNull(0)?.order}];  <${list.joinToString()}>" }.joinToString("\n")};   ")
//				Thread.sleep(1000)
//			}
//			analize()
//			Thread.sleep(1000)
//			for (info in infos) println("~# FINISHED $info:  done= ${info.doneCount}:  ${if (info.oopsCount != 0) "   !!! ${info.oopsCount};  " else ""}")
//			println("Entities::   ${entities.map { "E[${it.numId}] ${it.total}" }.joinToString(", ")};   ")
//
//		}
//	}
//	@JvmName("abort") @JvmStatic
//	internal fun abort(ms: Int, msg: String, soft:Boolean = false) {
//		deadline = now()
//		debugState = 1
//		println("~# ABORTING !!!!!  due to:\n        $msg")
//		if (!soft) thread {
//			Thread.sleep(ms.toLong())
//			for (t in threads) t.stop()
//			println("~# ABORTED !!!!!  due to:\n        $msg")
//		}
//	}
//
//	fun profileMemo() = Thread(Runnable {
//		val runtime = Runtime.getRuntime()
//		val MEGABYTE = 1024L * 1024L
//		while (!exiting) {
//			synchronized(memo!!) { (memo as java.lang.Object).wait(30000) }
//			runtime.gc()
//			Thread.sleep(100)
//			val memory = runtime.totalMemory() - runtime.freeMemory()
//			println("used memo= ${memory / MEGABYTE}")
//		}
//	}).apply { start() }
//
//	@JvmStatic
//	fun analize() {
//		val ok = tids.filterNotNull().all { t ->
//			val se = t.waitOn
//			if (se == null) entities.all { !it.contains(t) }
//			else se.locker != null && se.locker != t && se.contains(t) && entities.all { it == se || !it.contains(t) }
//		}
//		if (ok) return
//		val msg = dump()
//		println(msg)
//		abort(5, msg)
//	}
//
//	private fun dump(): String {
//		val buff = StringBuilder("Oops... ")
//		tids.filterNotNull().forEach { t ->
//			val se = t.waitOn
//			if (se == null) entities.forEach { if (it.contains(t)) buff.append("Non-stuck $t in E${it.numId} waiters\n") }
//			else {
//				if (se.locker == null) buff.append("$t waits E${se.numId} but it is unlocked\n")
//				else if (se.locker == t) buff.append("$t waits and locks E${se.numId}\n")
//				if (!se.contains(t)) buff.append("$t stuck to E${se.numId} but no in waiters\n")
//				entities.forEach { if (it != se && it.contains(t)) buff.append("$t in E${it.numId} waiters\n") }
//			}
//		}
//		buff.append(entities.map { "${if (it.locker == null) "---" else "${it.locker}"}[E${it.numId}]" }.joinToString("  ", "", ""))
//		  .append("   ::   ")
//		  .append(tids.map { "$it>${if (it?.waitOn == null) "---" else "E${it?.waitOn?.numId}"}" }.joinToString("  ", "", ""))
//		return buff.toString()
//	}
//
//	@JvmName("log") @JvmStatic
//	internal fun log(synt: Synt, current: ThreadInfo, n: Int, tag: String, tag2: String, extra: String): Unit {
//		if (Test.debugState > 0) return
//		val sTag = tag.padEnd(20 - tag.length, ' ')
//		val sTag2 = tag2.padEnd(12 - tag2.length, ' ')
//		println("$current > ${if (synt.locker == null) "---" else "${synt.locker}"}[${synt.numId}]:  $sTag$tag2            $extra    #$n")
//	}
//
//}
