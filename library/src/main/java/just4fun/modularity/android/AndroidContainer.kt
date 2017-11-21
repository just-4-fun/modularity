package just4fun.modularity.android

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.util.Log
import just4fun.kotlinkit.*
import just4fun.modularity.android.ActivityState.DESTROYED
import just4fun.modularity.android.async.AndroidThreadContext
import just4fun.modularity.android.async.AndroidThreadContextBuilder
import just4fun.modularity.core.BaseModule
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.multisync.ThreadInfo.info
import kotlin.reflect.KClass


/* CONTAINER */

/** A module container for Android.
 * An instance should be created in [Application.onCreate] callback with the [Application] instance passed to the constructor as [appContext].
 *
 *
 */
abstract class AndroidContainer(val appContext: Application): ModuleContainer() {
	/** The reference to the topmost [Activity] if any. */
	val uiContext: Activity? get() = ui.primary
	//
	override val ThreadContexts by lazy { AndroidThreadContextBuilder() }
	internal val ui = ActivityTracker(this).apply { appContext.registerActivityLifecycleCallbacks(this) }
	internal var float: FloatManager? = null
	private val refs = mutableListOf<AndroidModuleReference<*>>()// no need to sync since is called from main thread
	private var reconfig: Boolean = false
	override val debugInfo: AndroidDebugInfo? = null
	
	init {
		DEBUG = isDebug()
		if (DEBUG) logFun = { priority, id, msg -> Log.println(priority, id.toString(), "${time}    ${info.get()} [$id]::    $msg") }
		if (checkFloat()) float = FloatManager(this)
	}
	
	/** Instead of [onPopulated] */
	open protected fun onContainerPopulated() {}
	
	/** Instead of [onEmpty] */
	open protected fun onContainerEmpty() {}
	
	/** The callback captures most general UI events (events of all app's [Activity]s). See [UiPhase]. */
	open protected fun onUiPhaseChange(phase: UiPhase) {}
	
	/** Puts the app into Foreground so that it wouldn't be killed by the system. The action is based on [Service.startForeground] and utilizes internal [FloatService]. */
	fun startForeground(id: Int, notification: Notification) = float?.startForeground(id, notification)
	
	/** Puts the app back into Background. The action is based on [Service.stopForeground] and utilizes internal [FloatService]. */
	fun stopForeground(removeNotification: Boolean) = float?.stopForeground(removeNotification)
	
	/* internal */
	
	override fun <M: BaseModule> moduleReference(moduleKClass: KClass<M>, bindID: Any?): AndroidModuleReference<M> = AndroidModuleReference(moduleKClass, bindID)
	
	override fun onCreateThreadContext() = AndroidThreadContext(false).apply { ownerToken = this }
	
	internal fun uiPhaseChange(value: UiPhase) {
		if (value >= UiPhase.HIDDEN && !isEmpty) float?.start()
		Safely { onUiPhaseChange(value) }
		if (value <= UiPhase.SHOWN) float?.stop()
	}
	
	/** Use [onContainerPopulated] */
	override final fun onPopulated() {
		if (ui.phase >= UiPhase.HIDDEN) float?.start()
		onContainerPopulated()
	}
	
	/** Use [onContainerEmpty] */
	override final fun onEmpty() {
		onContainerEmpty()
		float?.stop()
	}
	
	internal fun activityStateChange(activity: Activity, primary: Boolean, state: ActivityState) {
		debugInfo?.onActivityStateChange(activity, primary, state.toString())
		if (state == DESTROYED) {
			if (activity.isChangingConfigurations) {
				reconfig = true
				refs.forEach { if (it.activity == activity) it.activity = null }
			} else removeUiRef(activity)
		} else if (reconfig) {
			removeUiRef(null)
			reconfig = false
		}
	}
	
	/* Ui Ref mgt */
	private fun removeUiRef(activity: Activity?) = refs.retainAll { (it.activity != activity).apply { if (!this) it.unbindModule() } }
	
	internal fun <M: UIModule<*>> addUiRef(activity: Activity, moduleKClass: KClass<M>, bindId: Any?): M {
		fun matches(ref: AndroidModuleReference<*>, a: Activity, c: KClass<*>, b: Any?) = ref.moduleKClass == c && ref.activity == a && ref.bindID == b
//		log("UiConns", "${refs.size}")
		@Suppress("UNCHECKED_CAST")
		return refs.find { matches(it, activity, moduleKClass, bindId) }?.module as? M ?: run {
			ui.onActivityCreating(activity)
			val ref = moduleReference(moduleKClass, bindId).apply { this.activity = activity }
			val module = ref.bindModule()
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
	
	
	
	
	/* Reference */
	
	open inner class AndroidModuleReference<M: BaseModule>(moduleKClass: KClass<M>, bindID: Any?): ModuleReference<M>(moduleKClass, bindID) {
		internal var activity: Any? = null
	}
	
	
	open inner class AndroidDebugInfo: DebugInfo() {
		// TODO
		open fun onActivityStateChange(activity: Activity, primary: Boolean, state: String) {
			//			val reconfiguring = activity.isChangingConfigurations
			//			val finishing = activity.isFinishing
			//			val id = activity.hashCode().toString(16)
			//			val reason = if (finishing) "finishing" else if (reconfiguring) "reconfiguring" else "other"
			//			log("${activity::class.simpleName}", "id= $id;  prim= $primary;  reason= $reason;  state= $state")
		}
	}
}





