package just4fun.modularity.android

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.content.Intent
import android.util.Log
import just4fun.kotlinkit.Safely
import just4fun.kotlinkit.DEBUG
import just4fun.kotlinkit.log
import just4fun.kotlinkit.logFun
import just4fun.kotlinkit.time
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.multisync.ThreadInfo.info
import just4fun.modularity.android.ActivityState.*


/* CONTAINER */

abstract class AndroidContainer(internal val appContext: Application): ModuleContainer() {
	override val ExecutionContexts by lazy { AndroidExecutionContextBuilder() }
	internal val ui = ActivityTracker(this).apply { appContext.registerActivityLifecycleCallbacks(this) }
	internal var float: FloatManager? = null
	private val refs = mutableListOf<ModuleReference<*>>()// no need to sync since is called from main thread
	private var reconfig: Boolean = false
	val uiContext: Activity? get() = ui.primary
	
	init {
		//  TODO is there a better way ?  If user resets an excessive instance is created
		ExecutionContexts.SHARED = ExecutionContexts.newHANDLER
		DEBUG = isDebug()
		if (DEBUG) logFun = { priority, id, msg -> Log.println(priority, id.toString(), "${time}    ${info.get()} [$id]::    $msg") }
		if (checkFloat()) float = FloatManager(this)
	}
	
	open protected fun onContainerPopulated2() {}
	open protected fun onContainerEmpty2() {}
	open protected fun onUiPhaseChange(phase: UiPhase) {}
	
	fun startForeground(id: Int, notification: Notification) = float?.startForeground(id, notification)
	fun stopForeground(removeNotification: Boolean) = float?.stopForeground(removeNotification)
	
	
	internal fun uiPhaseChange(value: UiPhase) {
		log("Ui", "$value")
		if (value >= UiPhase.HIDDEN && !isEmpty) float?.start()
		Safely { onUiPhaseChange(value) }
		if (value <= UiPhase.SHOWN) float?.stop()
	}
	
	override final fun onContainerPopulated() {
		if (ui.phase >= UiPhase.HIDDEN) float?.start()
		onContainerPopulated2()
	}
	
	override final fun onContainerEmpty() {
		onContainerEmpty2()
		float?.stop()
	}
	
	internal fun activityStateChange(activity: Activity, primary: Boolean, state: ActivityState) {
		//TODO visible event broadcast to implement flow handlers
		if (state == DESTROYED) {
			if (activity.isChangingConfigurations) {
				reconfig = true
				refs.forEach { if (it.extra == activity) it.extra = null }
			} else removeUiRef(activity)
		} else if (reconfig) {
			removeUiRef(null)
			reconfig = false
		}
	}
	
	/* Ui Connector mgt */
	internal fun removeUiRef(activity: Activity?) = refs.retainAll { (it.extra != activity).apply { if (!this) it.unbind() } }
	internal fun <M: UiModule<*>> addUiRef(activity: Activity, moduleClass: Class<M>, bindId: Any?): M {
		fun matches(ref: ModuleReference<*>, a: Activity, c: Class<*>, b: Any?) = ref.moduleClass == c && ref.extra == a && ref.bindID == b
		log("UiConns", "${refs.size}")
		@Suppress("UNCHECKED_CAST")
		return refs.find { matches(it, activity, moduleClass, bindId) }?.module as? M ?: run {
			ui.onActivityConstructed(activity)
			val ref = moduleRef(moduleClass, bindId).apply { extra = activity }
			val module = ref.bind().valueOrThrow
			refs += ref
			module
		}
	}
	
	
	/* misc */
	private fun checkFloat(): Boolean {
		val clas: Class<*> = FloatService::class.java
		val intent = Intent(appContext, clas)
		val resolvers = appContext.packageManager.queryIntentServices(intent, 0)
		val registered = resolvers?.isNotEmpty() ?: false
		if (!registered) Safely {
			throw Exception("${clas.simpleName} service ensures application operation and needs to be declared in  AndroidManifest.xml\n<service android:name=\"${clas.name}\"/>")
		}
		return registered
	}
	
	private fun isDebug() = Safely {
		val clas = Class.forName(appContext.packageName + ".BuildConfig")
		val valueF = clas.getDeclaredField("DEBUG")
		valueF.isAccessible = true
		valueF.getBoolean(null)
	} ?: false
}





