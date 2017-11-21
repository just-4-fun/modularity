package just4fun.modularity.core.multisync;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

import just4fun.modularity.core.utils.EasyListElement;


public class ThreadInfo implements EasyListElement<ThreadInfo> {
private static AtomicInteger nextId = new AtomicInteger(0);
// public static ThreadLocal<ThreadInfo> info = ThreadLocal.withInitial(() -> new ThreadInfo(nextId.getAndIncrement()));
public static ThreadLocal<ThreadInfo> info = new ThreadLocal<ThreadInfo>() {
	@Override protected ThreadInfo initialValue() {
		return new ThreadInfo(nextId.getAndIncrement());
	}
};

int id = 0;
Sync waitOn;
boolean waiting;
int priority;
boolean tamper;
long order = 0L;
private ThreadInfo prev = null;
private ThreadInfo next = null;
// Thread thread = Thread.currentThread();// TODO for test
// int doneCount = 0;// TODO for test
// int oopsCount = 0;// TODO for test

@Override public String toString() { return id + ""; }

public ThreadInfo(int id) {
	this.id = id;
	// Thread.currentThread().setName(id+"");// TODO for test
	// thread.setName("T" + id);// TODO for test
	// Test.addInfo(this);// TODO for test
}

boolean outranks(ThreadInfo other) {
	return (!tamper && other.tamper) || (tamper == other.tamper && priority >= other.priority);
}

@Nullable @Override public ThreadInfo getNext() { return next; }
@Override public void setNext(@Nullable ThreadInfo e) { next = (ThreadInfo) e; }
@Nullable @Override public ThreadInfo getPrev() { return prev; }
@Override public void setPrev(@Nullable ThreadInfo e) { prev = (ThreadInfo) e; }
}
