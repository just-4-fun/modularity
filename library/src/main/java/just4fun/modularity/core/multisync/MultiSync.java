package just4fun.modularity.core.multisync;

import java.util.concurrent.Callable;

import static just4fun.modularity.core.multisync.OnDeadlock.Defer;
import static just4fun.modularity.core.multisync.OnDeadlock.Tamper;

// import java.util.function.Function;


class MultiSync {
private final static Object lock = new Object();
final static Object NULL = new Object();
// private static Function<Throwable, Unit> logger = null;
private static FunctionWrapper<Throwable, kotlin.Unit> logger = null;

static void log(Throwable error) {
	if (logger == null) error.printStackTrace();
	else logger.apply(error);
}
// static void setLogger(Function<Throwable, kotlin.Unit> logger) { MultiSync.logger = logger; }
static void setLogger(FunctionWrapper<Throwable, kotlin.Unit> logger) { MultiSync.logger = logger; }

private static boolean inOrder(Synt synt, long order) {
	synchronized (lock) { return synt.isEmpty() || order <= synt.getHead().order; }
}
private static void invokeDeferred(Synt synt, ThreadInfo current) {
	while (synt.deferred != null && inOrder(synt, synt.deferred.order)) {// to keep the order of requests
		// Test.log(synt, current, -1, ">", "", "posting...");// TODO for test
		synt.deferred.invoke();
		// Thread.interrupted();// todo ??? clears interrupted status if any
		synt.deferred = synt.deferred.next;
	}
}
private static <T> void defer(Synt synt, int priority, Callable<T> task, FunctionWrapper<T, kotlin.Unit> afterSyncTask, long order) {
// private static <T> void defer(Synt synt, int priority, Callable<T> task, Function<T, kotlin.Unit> afterSyncTask, long order) {
	// No need to sync as now its blocked by a deadlock
	Deferred<T> deferred = new Deferred<>(synt, priority, task, afterSyncTask, order);
	if (synt.deferred == null) synt.deferred = deferred;
	else synt.deferred.chain(deferred);
}

private static ThreadInfo findBlocker(ThreadInfo current, ThreadInfo blocker) {
	Synt waitOn = blocker.waitOn;
	while (waitOn != null) {
		if (waitOn.locker == current) return blocker;
		blocker = waitOn.locker;
		waitOn = blocker == null ? null : blocker.waitOn;
	}
	return null;
}
private static void awaitCurrent(Synt synt, ThreadInfo current) {
	current.waitOn = synt;
	synt.add(current);
}
private static void awake(Synt synt, ThreadInfo thread, boolean lock) {
	synchronized (thread) {
		if (lock) synt.locker = thread;// :)
		// thread.waitOn can be null if it is the first waiting thread and is interrupted. Than unlock block is immediately reached and this method is invoked from there.
		if (thread.waitOn != null) thread.waitOn.remove(thread);
		thread.waitOn = null;
		if (thread.waiting) {
			thread.waiting = false;
			thread.notify();
		}
	}
}
static <T> T SYNC(Synt synt, int priority, OnDeadlock resolution, Callable<T> task, FunctionWrapper<T, kotlin.Unit> afterSyncTask) throws Exception {
// static <T> T SYNC(Synt synt, int priority, OnDeadlock resolution, Callable<T> task, Function<T, kotlin.Unit> afterSyncTask) throws Exception {
	ThreadInfo current = ThreadInfo.info.get();
	current.priority = priority;
	current.resolution = resolution;
	// int n = current.doneCount++;// TODO for test
	// Test.log(synt, current, n, "", "", resolution + ":" + priority);// TODO for test
	boolean initialLock = true;
	long order;
	/* LOCK ***********************/
	synchronized (lock) {
		order = synt.total++;
		current.order = order;
		if (synt.locker == null) synt.locker = current;
		else if (synt.locker == current) initialLock = false;
		else {
			ThreadInfo blocker = findBlocker(current, synt.locker);
			if (blocker == null) awaitCurrent(synt, current);
			else {

				//TODO remove
				try {
					throw new Exception("");
				} catch (Exception x) {
					System.out.println("Oops.. Deadlock detected: " + current + "[" + synt + "]" + " -> " + blocker + "[" + blocker.waitOn + "]");
					x.printStackTrace();
				}

				if (current.outranks(blocker)) {
					awaitCurrent(synt, current);
					awake(synt, blocker, false);
				}
			}
			// if (blocker != null) {// TODO for test
			// 	String numId = blocker.waitOn == null ? "---" : blocker.waitOn.numId + "";// TODO for test
			// Test.log(synt, current, n, "@", "", blocker + "[" + numId + "]" + ";    loser: " + (current.outranks(blocker) ? blocker : current) + "   " + resolution);// TODO for test
			// }// TODO for test
		}
		// Test.analize();// TODO for test
	}
	/* WAIT ***********************/
	// assert (current.waitOn == null || current.waitOn == synt);// TODO for test
	boolean interrupted = false;
	synchronized (current) {
		while (current.waitOn == synt) {
			try {
				// Test.log(synt, current, n, "- - |", "", "");// TODO for test
				current.waiting = true;
				current.wait();
			} catch (InterruptedException x) {
				interrupted = true;
				if (current.waitOn == synt) {
					synt.remove(current);
					current.waitOn = null;
					current.waiting = false;
				}
			}
		}
	}
	// wait until unlock block calls awake and exits
	if (interrupted) synchronized (lock) { }
	// Test.log(synt, current, n, "- - - - " + (synt.locker == current ? ">" : "~   " + resolution), "", "");// TODO for test
	// assert (current.waitOn == null);// TODO for test
	/* EXEC ***********************/
	T result = (T) NULL;
	try {
		if (current == synt.locker || resolution == Tamper) {
			result = task.call();
		}
		else if (resolution == Defer) {
			defer(synt, priority, task, afterSyncTask, order);
		}
	}
	/* UNLOCK ***********************/ finally {
		if (current == synt.locker && initialLock) {
			if (synt.deferred != null) invokeDeferred(synt, current);
			synchronized (lock) {
				if (synt.isEmpty()) synt.locker = null;
				else awake(synt, synt.getHead(), true);
				// Test.analize();// TODO for test
			}
		}
	}
	/* AFTER UNLOCK ***********************/
	if (afterSyncTask != null && result != NULL) afterSyncTask.apply(result);
	// Test.log(synt, current, n, "X", "", "");// TODO for test
	return result;
}

}














