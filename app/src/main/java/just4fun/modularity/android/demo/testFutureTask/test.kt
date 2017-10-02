package just4fun.modularity.android.app.testFutureTask

import just4fun.modularity.android.demo.rnd
import just4fun.modularity.android.demo.rnd0
import just4fun.modularity.android.AndroidExecutionContext
import just4fun.kotlinkit.async.AsyncTask
import just4fun.kotlinkit.async.DefaultExecutionContext
import just4fun.kotlinkit.async.TaskContext
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import java.lang.System.currentTimeMillis as now



val duration = 10000
val deadline = duration + now()
val maxDelay = 100
val S = AndroidExecutionContext(false)
val tasks = mutableListOf<ATask>()
val IDs = AtomicInteger()
var successes = AtomicInteger()

object Test {
	
	fun run() {
//				FutureTask.scheduler = AndroidExecutionContext(false)
		AsyncTask.sharedContext = DefaultExecutionContext()
		println("TESTING........................ !!!!!!!!!!!")
		runTaskPool()
		runTaskPool()
		runTaskPool()
		runTaskPool()
		runExecurorConroller()
	}
	
	fun runExecurorConroller() {
		thread {
			while (now() < deadline) {
				control()
				val delay = rnd0(maxDelay * 2)
				Thread.sleep(rnd0(delay).toLong())
			}
			//
			S.pause()
			Thread.sleep(maxDelay*2L)
			S.shutdown(maxDelay)
			AsyncTask.sharedContext.shutdown()
			if (tasks.isNotEmpty()) println("-------------------------------------------- tasks Ok= ${successes.get()};  tasks LEFT= ${tasks.size}  from  ${IDs.get()}")
			println("TESTING DONE  Ok= ${successes.get()}  from  ${IDs.get()} ........................ !!!!!!!!")
		}
	}
	
	fun control() {
		val pause = rnd.nextBoolean()
		if (pause) S.pause() else S.resume()
		println("S >  ${if (pause) "pause" else "resume"}")
	}
	
	fun runTaskPool() {
		thread {
			while (now() < deadline) {
				val delay = rnd0(maxDelay)
				val executor = if(rnd.nextBoolean()) S else null
				val id = IDs.incrementAndGet()
				ATask(id, delay, executor, task(id))
				Thread.sleep(rnd0(maxDelay / 2).toLong())
			}
		}
	}
	
	fun task(id: Int): TaskContext.() -> Int ={
//		println("...  ${id}")
		id
	}
}


class ATask(val id: Int, val delay:Int, executor: Executor?, code: TaskContext.() -> Int) : AsyncTask<Int>(delay, executor, code) {
	init {
		tasks.add(this)
		onComplete {
			if (it.hasValue) successes.incrementAndGet()
			tasks.remove(this)
		}
	}
}