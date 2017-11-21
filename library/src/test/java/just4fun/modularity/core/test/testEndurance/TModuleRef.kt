package just4fun.modularity.core.test.testEndurance

import just4fun.modularity.core.Module
import just4fun.modularity.core.test.rnd
import just4fun.modularity.core.test.rnd0
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass


val bindLock = Any()

class TModuleRef(val id: Int) {
	private val lock = Any()
	val klas = (Class.forName("$packageName.M$id") as Class<TModule>).kotlin
	var level = 0
	var module: TModule? = null
	var predec: TModule? = null
	val boundRefs = CopyOnWriteArrayList<TModuleRef>()
	val unbounding = CopyOnWriteArrayList<Int>()
	val binderRefs = CopyOnWriteArrayList<TModuleRef>()
	// cfg
	var executorOption = rnd(ExecutorOption.values())
	var startRestful = rnd.nextBoolean()
	var restDelay = rnd0(10) * 100
	var restDuration = if(rnd.nextBoolean()) rnd0(10) * 100 else 0
	var activateOpt = rnd0(2)
	var activateDelay = rnd0(10) * 100
	var deactivateOpt = rnd0(2)
	var deactivateDelay = rnd0(10) * 100
	var needFinalizing = rnd.nextBoolean()
	val dumpConfig get() = "$executorOption,  R: $startRestful/$restDelay/$restDuration,  A: $activateOpt/$activateDelay,  D: $deactivateOpt/$deactivateDelay,  F: $needFinalizing;  Lv: $level"
	
	override fun toString() = "M$id"
	
	fun canBind(boundId: Int): Boolean = synchronized(bindLock) {
		if (id == boundId) return false
		val boundRef = moduleRefs[boundId]
		if (boundRefs.contains(boundRef)) return false
		val cyclic = isCyclicBinding(boundRef, mutableListOf())
		return if (cyclic) false else boundRef.addBinder(this)
	}
	
	private fun isCyclicBinding(boundRef: TModuleRef, callers: MutableList<TModuleRef>): Boolean {
		return binderRefs.any {
//			log(2, id, "binder: $it;  bound: $boundRef;  callers: $callers")
			!callers.contains(it) && (it === boundRef || it.isCyclicBinding(boundRef, callers.apply { add(this@TModuleRef) }))
		}
	}
	
	fun removeBound(boundRef: TModuleRef) = boundRefs.remove(boundRef)
	fun removeBinder(binderRef: TModuleRef) = synchronized(bindLock) {
		binderRefs.remove(binderRef)
		binderRef.removeBound(this)
	}
	
	fun addBound(boundRef: TModuleRef) = if (!boundRefs.contains(boundRef)) boundRefs.add(boundRef) else Unit
	private fun addBinder(binderRef: TModuleRef): Boolean {
		if (!binderRefs.contains(binderRef)) {
			binderRefs.add(binderRef)
			binderRef.addBound(this)
			level = binderRef.level + 1
		}
		return true
	}
		
	fun moduleDestroyed(m: TModule) = synchronized(bindLock) {
		if (module === m) {
			boundRefs.forEach { it.removeBinder(this) }
//			binderRefs.clear()
			module = null
			genConfig()
		}
		if (predec === m) predec = null
		activeModules.remove(m)
	}
	
	fun isActiveModule(m: TModule) = module === m
	fun setActiveModule(m: TModule) = synchronized(bindLock) {
		predec = module
		module = m
		activeModules.add(m)
	}
	
	private fun genConfig() {
		executorOption = rnd(ExecutorOption.values())
		startRestful = rnd.nextBoolean()
		restDuration =  if(rnd.nextBoolean()) rnd0(10) * 100 else 0
		restDelay = rnd0(10) * 100
		activateDelay = rnd0(10) * 100
		activateOpt = rnd0(2)
		deactivateDelay = rnd0(10) * 100
		deactivateOpt = rnd0(2)
		needFinalizing = rnd.nextBoolean()
	}
	
	fun reset() {
		level = 0
		module =null
		predec = null
		binderRefs.clear()
		genConfig()
	}
}
