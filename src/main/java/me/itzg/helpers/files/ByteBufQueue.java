package me.itzg.helpers.files;

import io.netty.buffer.ByteBuf;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class ByteBufQueue {

    final Lock lock = new ReentrantLock();
    final Condition readyOrFinished = lock.newCondition();
    final LinkedList<ByteBuf> buffers = new LinkedList<>();
    boolean finished = false;

    public void add(ByteBuf buf) {
        lock.lock();
        try {
            buffers.add(buf);
        } finally {
            readyOrFinished.signal();
            lock.unlock();
        }
    }

    public ByteBuf take() {
        while (true) {
            lock.lock();

            try {
                if (!buffers.isEmpty()) {
                    return buffers.removeFirst();
                }
                else if (finished) {
                    return null;
                }
                readyOrFinished.awaitUninterruptibly();
            } finally {
                lock.unlock();
            }
        }
    }

    public void finish() {
        lock.lock();
        try {
            finished = true;
            readyOrFinished.signal();
        } finally {
            lock.unlock();
        }
    }
}
