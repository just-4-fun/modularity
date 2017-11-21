package just4fun.modularity.core.test.testInjection

import just4fun.modularity.core.Module
import just4fun.modularity.core.ModuleContainer
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.SuspendUtils
import org.junit.Test



class Example {
	@Test fun run() {
		testInjection(false)
		testInjection(true)
	}
}

fun testInjection(isDebug: Boolean) {
	val container = SomeContainer(isDebug)
	val ref = container.moduleReference(MainModule::class)
	val module = ref.bindModule()
	module.check(isDebug)
	module.use()
	ref.unbindModule()
	Thread.sleep(100)
	container.tryShutdown()
}

class SomeContainer(isDebug: Boolean): ModuleContainer() {
	init {
		val injectKlas = if (isDebug) DebugModule::class else ReleaseModule::class
		associate(BaseModule::class, injectKlas)// WARN: injection association
	}
}

class MainModule: Module<MainModule>(), ModuleImplement {
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
	override fun onCreateImplement() = this
	
	val module: BaseModule = bind(BaseModule::class)// WARN: injection usage
	
	fun use() = module.act()
	fun check(isDebug: Boolean) {
		val mClas = module::class
		val isOk = if (isDebug) mClas == DebugModule::class else mClas == ReleaseModule::class
		assert(isOk)
	}
}

class DebugModule: BaseModule() {
	override fun act() = println("DebugModule acts")
}

class ReleaseModule: BaseModule() {
	override fun act() = println("ReleaseModule acts")
}

open class BaseModule: Module<BaseModule>(), ModuleImplement {
	suspend override fun SuspendUtils.onActivate(first: Boolean) = Unit
	suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) = Unit
	override fun onCreateImplement() = this
	
	open fun act() = println("SomeModule acts")
}
//
//abstract class BaseServer: Module<BaseServer>(), ModuleImplement  {
//	abstract fun perform()
//}
//
//class TestServer : BaseServer(), ModuleImplement {
//	override fun perform() = println("Test")
//}
//
//class ReleaseServer : BaseServer(), ModuleImplement {
//	override fun perform() = println("Release")
//}
//
//class User : Module<User>(), ModuleImplement {
//	val server = bind(BaseServer::class)
//	fun use() = server.perform()// prints "Test" or "Release" depending on isDebug
//}
//
//class Container: ModuleContainer() {
//	init {
//		val injectKlas = if (isDebug) TestServer::class else ReleaseServer::class
//		associate(BaseServer::class, injectKlas)
//	}
//}
