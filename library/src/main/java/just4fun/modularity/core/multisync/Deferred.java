package just4fun.modularity.core.multisync;

import java.util.concurrent.Callable;



class Deferred<T> {
private Synt synt;
private int priority;
private Callable<T> task;
// private Function<T, Unit> afterSyncTask;
private FunctionWrapper<T, kotlin.Unit> afterSyncTask;
long order;
Deferred<?> next = null;

Deferred(Synt synt, int priority, Callable<T> task, FunctionWrapper<T, kotlin.Unit> afterSyncTask, long order) {
// Deferred(Synt synt, int priority, Callable<T> task, Function<T, kotlin.Unit> afterSyncTask, long order) {
	this.synt = synt; this.priority = priority; this.task = task; this.afterSyncTask = afterSyncTask; this.order = order;
}

void chain(Deferred<?> newHook) {
	if (next == null) next = newHook;
	else next.chain(newHook);
}

void invoke() {
	try {
		MultiSync.SYNC(synt, priority, OnDeadlock.Defer, task, afterSyncTask);
	} catch (Throwable x) {
		MultiSync.log(x);
	}
}


}
