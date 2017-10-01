package just4fun.modularity.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import just4fun.modularity.android.ActivityState.*
import just4fun.kotlinkit.DEBUG
import just4fun.kotlinkit.log



enum class UiPhase {CREATED, SHOWN, HIDDEN, DESTROYED }


internal enum class ActivityState {
	CREATED, STARTED, RESUMED, PAUSED, STOPPED, DESTROYED
}



/* ACTIVITY TRACKER */
// TODO Test roughly
/**

"The system never kills an activity directly. Instead, it kills the process in which the activity runs, destroying not only the activity but everything else running in the process, as well " (from Activity lifecycle doc). >> Means Activity exists unless it's destroyed or Process is killed.
 */
internal class ActivityTracker(private val container: AndroidContainer): Application.ActivityLifecycleCallbacks {
	var primary: Activity? = null
	var state: ActivityState = DESTROYED
	var paused: MutableList<Activity>? = null
	var phase: UiPhase = UiPhase.DESTROYED
		set(value) = run { field = value; container.uiPhaseChange(value) }
		get() = field
	private val lock = this
	val isVisible get() = phase == UiPhase.SHOWN
	val isAlive get() = phase != UiPhase.DESTROYED
	
	/* ui ref */
	@Suppress("UNCHECKED_CAST")
	fun <A: Activity> getIfVisible(activityClass: Class<A>): A? {
		if (state < RESUMED || state > PAUSED) return null
		if (primary != null && primary!!::class.java == activityClass) return primary as A
		val a = synchronized(lock) { paused?.find { it::class.java == activityClass } }
		return a as? A?
	}
	
	private fun assignPrimary(a: Activity) {
		if (a == primary) return
		removePaused(a)
		primary?.let { if (state == PAUSED && !it.isFinishing) addPaused(it) }
		primary = a
	}
	
	private fun addPaused(a: Activity) = synchronized(lock) {
		paused = (paused ?: mutableListOf()).also { it += a }
	}
	
	private fun removePaused(a: Activity) = synchronized(lock) {
		paused = paused?.let { it -= a; if (it.isEmpty()) null else it }
	}
	
	/* Life cycle callbacks */
	
	fun onActivityConstructed(a: Activity) {
		if (phase == UiPhase.DESTROYED) phase = UiPhase.CREATED
	}
	
	override fun onActivityCreated(a: Activity, inState: Bundle?) {
		assignPrimary(a)
		if (phase == UiPhase.DESTROYED) phase = UiPhase.CREATED
		onStateChange(a, CREATED)
		//			if (inState != null) fireRestore(a, inState)
	}
	
	override fun onActivityStarted(a: Activity) {
		assignPrimary(a)
		onStateChange(a, STARTED)
	}
	
	override fun onActivityResumed(a: Activity) {
		assignPrimary(a)
		if (phase != UiPhase.SHOWN) phase = UiPhase.SHOWN
		onStateChange(a, RESUMED)
	}
	
	override fun onActivityPaused(a: Activity) {
		onStateChange(a, PAUSED)
	}
	
	override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) {}//= fireSave(a, outState)
	
	override fun onActivityStopped(a: Activity) {
		removePaused(a)
		if (primary == a && !a.isChangingConfigurations) phase = UiPhase.HIDDEN
		onStateChange(a, STOPPED)
	}
	
	override fun onActivityDestroyed(a: Activity) {
		if (primary == a && !a.isChangingConfigurations) phase = UiPhase.DESTROYED
		if (primary == a) primary = null
		onStateChange(a, DESTROYED)
	}
	
	private fun onStateChange(a: Activity, s: ActivityState) {
		val isPrimary = primary == null || primary == a
		if (isPrimary) state = s
		if (DEBUG) log(a, isPrimary, s)
		container.activityStateChange(a, isPrimary, s)
	}
	
	private fun log(activity: Activity, primary: Boolean, state: ActivityState) {
		val reconfiguring = activity.isChangingConfigurations
		val finishing = activity.isFinishing
		val id = activity.hashCode().toString(16)
		val reason = if (finishing) "finishing" else if (reconfiguring) "reconfiguring" else "other"//TODO depend on state
		log("${activity::class.simpleName}", "id= $id;  prim= $primary;  reason= $reason;  state= $state")
	}
	
	//		private fun fireRestore(a: Activity, inState: Bundle) {}
	//		private fun fireSave(a: Activity, outState: Bundle) {}
}


