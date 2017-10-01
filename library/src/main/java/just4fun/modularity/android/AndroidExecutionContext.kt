package just4fun.modularity.android

import android.os.*
import just4fun.modularity.android.ExecutionContextState.*
import just4fun.kotlinkit.async.AsyncTask
import just4fun.kotlinkit.async.ExecutionContext
import just4fun.kotlinkit.async.ExecutionContextBuilder
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.log
import java.util.concurrent.RejectedExecutionException


open class AndroidExecutionContextBuilder: ExecutionContextBuilder() {
	val MainHANDLER: ExecutionContext by lazy{ AndroidExecutionContext(true).apply { owner = this }}
	val newHANDLER: ExecutionContext get() = AndroidExecutionContext(false)
}



internal enum class ExecutionContextState {RESUMED, PAUSED, SHUTDOWN; }

/**
WARN: Do not use an instance of this class as a synchronization lock object.
 */
open class AndroidExecutionContext(val executeInUiThread: Boolean = false): ExecutionContext() {
	private var state = RESUMED
	private val lock = this
	private var handler: Handler? = null
	private var count = 0
	
	override fun execute(task: Runnable) = synchronized(lock) {
		if (state == SHUTDOWN) throw RejectedExecutionException("Executor has been shutdown")
		val h = handler ?: startHandler()
		if (h.post(task)) count++
	}
	
	override fun onSchedule(task: AsyncTask<*>) = synchronized(lock) {
		if (state == SHUTDOWN) {
			task.cancel(RejectedExecutionException("Executor has been shutdown"))
			return
		}
		val h = handler ?: startHandler()
		if (h.postAtTime(task, task, task.delayMs + SystemClock.uptimeMillis())) count++
	}
	
	override fun onRemove(task: AsyncTask<*>) = synchronized(lock) {
		if (task.isCancelled) {
			handler?.removeCallbacks(task)
			if (--count == 0 && state == PAUSED) stopHandler()
		}
	}
	
	override fun resume() = synchronized(lock) {
		if (state == PAUSED) state = RESUMED
	}
	
	override fun pause() = synchronized(lock) {
		if (state == RESUMED) state = PAUSED
		if (count == 0) stopHandler()
	}
	
	override fun shutdown(await: Int) = synchronized(lock) {
		state = SHUTDOWN
		if (count == 0 || await == 0) stopHandler()
		else Thread {
			val t = SystemClock.uptimeMillis() + await
			val step = StrictMath.max(await / 4, 40).toLong()
			while (count > 0 && t > SystemClock.uptimeMillis()) Safely { Thread.sleep(step) }
			stopHandler()
		}.start()
	}
	
	
	private fun stopHandler() {
		// TODO just for test
		if (count > 0) log("Executor", "WARNING!!!  there are $count  requests unhandled")
		
		if (executeInUiThread) handler?.removeCallbacksAndMessages(null)
		else handler?.looper?.quit()
		handler = null
	}
	
	private fun startHandler(): Handler {
		val looper = if (executeInUiThread) Looper.getMainLooper() else run {
			val thread = HandlerThread("Handler:$owner")
			thread.start()
			thread.looper
		}
		val h = object: Handler(looper) {
			override fun dispatchMessage(msg: Message) {
				if (msg.callback != null) {
					synchronized(lock) { if (--count == 0 && state >= PAUSED) stopHandler() }
					msg.callback.run()
				}
			}
		}
		handler = h
		return h
	}
}
