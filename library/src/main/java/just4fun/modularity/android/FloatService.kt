package just4fun.modularity.android

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import just4fun.modularity.android.FloatState.*
import just4fun.modularity.core.ModuleContainer
import just4fun.kotlinkit.async.AsyncTask
import just4fun.kotlinkit.log
import just4fun.kotlinkit.Safely

class FloatService: Service() {
	private val mgr = (ModuleContainer.current as AndroidContainer).float
	
	override fun onBind(p0: Intent?): IBinder? = null
	override fun onStartCommand(i: Intent, f: Int, d: Int): Int {
		if (mgr != null) return mgr.onStart(this)
		stopSelf()
		return Service.START_REDELIVER_INTENT
	}
	
	override fun onDestroy() {
		mgr?.onDestroy(this)
	}
}


internal enum class FloatState {CREATING, STARTED, STOPPING, DESTROYED }

internal class FloatManager(private val container: AndroidContainer) {
	private var service: Service? = null
	private var state = DESTROYED//; set(value) = run { field = value; log("Float", state.toString()) }
	private var notification: Notification? = null
	private var notificationId: Int = 0
	private val foreground: Boolean get() = notification != null
	private val lock = this
	
	/* foreground control */
	
	fun startForeground(notificationId: Int, notification: Notification) {
		this.notification = notification
		this.notificationId = notificationId
		synchronized(lock) { if (state == STARTED) service?.startForeground(notificationId, notification) }
	}
	
	fun stopForeground(removeNotification: Boolean) {
		notification = null
		synchronized(lock) { if (state == STARTED) service?.stopForeground(removeNotification) }
	}
	
	/* service control */
	fun start(): Unit = synchronized(lock) {
		if (state <= STARTED) return
		val isStopping = state == STOPPING
		state = CREATING
		if (isStopping) return
		startService()
	}
	
	fun stop(): Unit = synchronized(lock) {
		if (state >= STOPPING) return
		val isCreating = state == CREATING
		state = STOPPING
		if (isCreating) return
		service?.stopSelf()
	}
	
	private fun startService() = Safely {
		val intent = Intent(container.appContext, FloatService::class.java)
		container.appContext.startService(intent)
	}
	
	
	/* service callbacks */
	fun onStart(s: FloatService): Int = synchronized(lock) {
		if (state == STOPPING || container.isEmpty || container.ui.phase <= UiPhase.SHOWN) {
			state = STOPPING
			s.stopSelf()
			return Service.START_NOT_STICKY
		}
		state = STARTED
		service = s
		if (foreground) s.startForeground(notificationId, notification)
		
//		// TODO REmove
//		if (spamtask == null) {
//			var counter = 0
//			spamtask = AsyncTask(10000) {
//				log("Float", "SPAM;  ${counter++}")
//				spamtask = (this as AsyncTask<*>).runCopy()
//			}
//		}
		
		// todo ???  get onto flags
		return Service.START_REDELIVER_INTENT
	}
	
//	todo remove
//	var spamtask: AsyncTask<*>? = null
	
	fun onDestroy(s: FloatService) = synchronized(lock) {
		if (service == s) service = null // else what ?
		if (state == CREATING) {
			startService()
			return
		}
		state = DESTROYED
		
//		//todo remove
//		spamtask?.cancel()
//		spamtask = null
		
	}
}
