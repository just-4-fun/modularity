package just4fun.modularity.android.async

import android.os.*
import just4fun.modularity.android.async.ThreadContextState.*
import just4fun.kotlinkit.async.AsyncTask
import just4fun.kotlinkit.async.ThreadContext
import just4fun.modularity.core.async.ThreadContextBuilder
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.log
import just4fun.modularity.core.Module
import java.util.concurrent.RejectedExecutionException


open class AndroidThreadContextBuilder: ThreadContextBuilder() {
	/** Single threaded context based on Android [Handler] which runs on the app's Main thread.  */
	val MAIN: ThreadContext by lazy{ AndroidThreadContext(true).apply { ownerToken = this }}
	/** Single threaded context based on Android [Handler].  */
	override fun MONO(ownerToken: Module<*>): ThreadContext = AndroidThreadContext(false).also { it.ownerToken = ownerToken }
}



internal enum class ThreadContextState {RESUMED, PAUSED, SHUTDOWN; }

/** Based on Android [Handler].
WARN: Do not use an instance of this class as a synchronization lock object.
 */
open class AndroidThreadContext(val executeInUiThread: Boolean = false): ThreadContext() {
	private var state = RESUMED
	private val lock = this
	private var handler: Handler? = null
	private var count = 0
	
	override fun execute(task: Runnable) = synchronized(lock) {
		if (state == SHUTDOWN) throw RejectedExecutionException("Executor has been shutdown")
		val h = handler ?: startHandler()
		if (h.post(task)) count++
	}
	
	override fun schedule(task: AsyncTask<*>) = synchronized(lock) {
		if (state == SHUTDOWN) {
			task.cancel(RejectedExecutionException("Executor has been shutdown"))
			return
		}
		val h = handler ?: startHandler()
		if (h.postAtTime(task, task, task.delayMs + SystemClock.uptimeMillis())) count++
	}
	
	override fun remove(task: AsyncTask<*>) = synchronized(lock) {
		if (task.isCancelled) {
			handler?.removeCallbacks(task)
			if (--count == 0 && state == PAUSED) stopHandler()
		}
	}
	
	fun resume() = synchronized(lock) {
		if (state == PAUSED) state = RESUMED
	}
	
	fun pause() = synchronized(lock) {
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
//		if (count > 0) log("Executor", "WARNING!!!  there are $count  requests unhandled")
		
		if (executeInUiThread) handler?.removeCallbacksAndMessages(null)
		else handler?.looper?.quit()
		handler = null
	}
	
	private fun startHandler(): Handler {
		val looper = if (executeInUiThread) Looper.getMainLooper() else run {
			val thread = HandlerThread("Handler:$ownerToken")
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
