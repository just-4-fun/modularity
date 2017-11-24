package just4fun.modularity.android.demo.TestDocs

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import just4fun.kotlinkit.Result
import just4fun.kotlinkit.async.AsyncResult
import just4fun.kotlinkit.async.SuspendTask
import just4fun.kotlinkit.async.ThreadContext
import just4fun.kotlinkit.flatten
import just4fun.modularity.android.*
import just4fun.modularity.android.demo.R
import just4fun.modularity.core.ModuleImplement
import just4fun.modularity.core.SuspendUtils
import kotlin.concurrent.thread


// Instantiate the `AndroidContainer` subclass in `onCreate` method
// of the `android.app.Application` subclass (do not forget to register it in the `AndroidManifest.xml`).

class App: Application() {
	override fun onCreate() {
		Container(this)
	}
}

class Container(app: App): AndroidContainer(app) {
	val ref = moduleReference(MainModule::class)
	
	// Here we use the app's Activity first launch to start the "main" module,
	// and an Activity exit to stop the "main" module.
	override fun onUiPhaseChange(phase: UiPhase) {
		if (phase == UiPhase.CREATED) ref.bindModule()
		else if (phase == UiPhase.DESTROYED) ref.unbindModule()
	}
	
	// The actual completion of any module activity is intercepted here.
	override fun onContainerEmpty() {
		Log.i("Container", "Container is empty")
	}
}

// The basic role of a "main" module is to initialize and coordinate other modules.
// Here the "main" module in addition handles the communication with UI.

class MainModule: UIModule<MainModule.Implement>() {
	val db: DBModule = bind(DBModule::class)
	val ui: MainActivity? by ActivityReference()
	
	fun loadData() {
		implement.runAsync { loadDataImpl() }
		  .onComplete { result -> ui?.showMessage(result.valueOrThrow) }
	}
	
	fun saveData(data: String): AsyncResult<Boolean> = implement.runAsync { saveDataImpl(data) }
	
	override fun onConstructed() = loadData()
	
	override fun onCreateImplement() = Implement()
	
	inner class Implement: ModuleImplement {
		suspend override fun SuspendUtils.onActivate(first: Boolean) {}
		suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {}
		
		suspend fun loadDataImpl(): String = db.loadData().valueOrThrow
		
		suspend fun saveDataImpl(data: String): Boolean = db.saveData(data).valueOrThrow
	}
}

// This module imitates interactions with a database.
// All implement's actions are performed in a separate thread as defined by the overridden thread context.

class DBModule: AndroidModule<DBModule.Implement>() {
	suspend fun loadData(): Result<String> = implement.runSuspend { loadDataImpl() }.flatten()
	
	suspend fun saveData(data: String): Result<Boolean> = implement.runSuspend { saveDataImpl(data) }.flatten()
	
	override fun onCreateImplement(): Implement = Implement()
	
	override fun onCreateThreadContext(): ThreadContext? = container.ThreadContexts.MONO(this)
	
	inner class Implement: ModuleImplement {
		lateinit var connection: DummyDatabase
		
		suspend override fun SuspendUtils.onActivate(first: Boolean) {
			connection = DummyDatabase()
			waitUnless { connection.isOpen }
		}
		
		suspend override fun SuspendUtils.onDeactivate(last: () -> Boolean) {
			connection.close()
			waitWhile { connection.isOpen }
		}
		
		suspend fun loadDataImpl(): Result<String> = connection.load()
		
		suspend fun saveDataImpl(data: String): Result<Boolean> = connection.save(data)
	}
}

// This class simulates database with long running initialization and operations.

class DummyDatabase {
	var isOpen: Boolean = false
	
	init {
		thread {
			Log.i("db", "Initializing")
			Thread.sleep(1000)
			Log.i("db", "Initialized")
			isOpen = true
		}
	}
	
	fun close() {
		isOpen = false
	}
	
	suspend fun load(): Result<String> = SuspendTask {
		Log.i("db", "Loading data.")
		Thread.sleep(1000)
		Log.i("db", "Loaded data.")
		"Initial data loaded"
	}
	
	suspend fun save(data: String): Result<Boolean> = SuspendTask {
		Log.i("db", "Saving data.")
		Thread.sleep(1000)
		Log.i("db", "Saved data.")
		true
	}
}

// The Activity binds and interacts with the MainModule.

class MainActivity: Activity(), UiModuleReference<MainModule> {
	override val module = bindModule(this, MainModule::class)
	val message by lazy { findViewById<TextView>(R.id.message) }
	
	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_example)
		findViewById<Button>(R.id.button).setOnClickListener { saveMessage() }
	}
	
	fun saveMessage() {
		val data = message.text.toString()
		message.text = ""
		module.saveData(data)
		  // `module.ui` returns a fresh instance of this Activity avoiding leakage
		  .onComplete { module.ui?.showMessage("Message is saved") }
	}
	
	fun showMessage(text: String) = runOnUiThread {
		message.text = text
		Log.i("ui", "Message:  $text")
	}
}


