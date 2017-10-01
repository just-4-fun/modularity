package just4fun.modularity.android.app.testFutureTask

import android.app.Application
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import just4fun.modularity.android.demo.R




class App: Application() {
	init {
		Test.run()
	}
}

/*Activity*/
class MainActivityKt: AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		val toolbar = findViewById(R.id.toolbar) as Toolbar
		setSupportActionBar(toolbar)
		val fab = findViewById(R.id.fab) as FloatingActionButton
		fab.setOnClickListener { view ->
			Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show()
		}
	}
	
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}
	
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		val id = item.itemId
		if (id == R.id.action_settings) return true
		return super.onOptionsItemSelected(item)
	}
}
