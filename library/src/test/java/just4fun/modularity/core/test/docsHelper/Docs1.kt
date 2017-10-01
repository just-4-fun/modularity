package just4fun.modularity.core.test.docsHelper



//fun main(args: Array<String>) {
//val container = object : ModuleContainer() {}
//val userModuleRef = container.moduleConnector(UserModule::class.java)
//userModuleRef.bind()
//userModuleRef.use {
//	doTheJob()
//}
//userModuleRef.unbind()
//}
//
//class UserModule : Module<UserModule>(), ModuleActivity {
//	val service: ServiceModule = bind(ServiceModule::class).valueOrThrow
//
//	fun doTheJob() {
//		service.saveData(someData)
//	}
//
//	override fun constructActivity(): UserModule = this
//
//	suspend override fun UserModule.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) = Unit
//
//	suspend override fun UserModule.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) = Unit
//}
//
//
//class ServiceModule: Module<ServiceModule.Activity>() {
//
//	inner class Activity : ModuleActivity {
//		lateinit var database: DatabaseConnection
//	}
//
//	fun saveData(data: Data) = executeWhenActive {
//		database.save(data)
//	}
//
//	override fun constructActivity(): Activity = Activity()
//
//	suspend override fun Activity.onActivate(progressUtils: ProgressUtils, isInitial: Boolean) {
//		database = Database.openConnection()
//	}
//
//	suspend override fun Activity.onDeactivate(progressUtils: ProgressUtils, isFinal: () -> Boolean) {
//		database.closeConnection()
//	}
//}

//Event mechanism

//class Event
//
//interface Listener {
//	fun handle(event: Event)
//}
//
//class A: Module<...>(), Listener {
//	override fun handle(event: Event) = println("Got EventB")
//}
//
//class B: Module<...>() {
//	val channelA = eventChannel(Listener::handle)
//	fun callModuleA() = channelA(Event())
//}