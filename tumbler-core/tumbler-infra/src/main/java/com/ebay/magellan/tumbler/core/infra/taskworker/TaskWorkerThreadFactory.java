package com.ebay.magellan.tumbler.core.infra.taskworker;

import com.ebay.magellan.tumbler.depend.common.thread.DefaultThreadFactory;
import org.springframework.stereotype.Component;

@Component
public class TaskWorkerThreadFactory extends DefaultThreadFactory {

    TaskWorkerThreadFactory() {
        super();
        namePrefix = "tumbler-task-worker-thread-";
    }

    public TaskWorkerThread buildTaskWorkerThread() {
        TaskWorkerThread taskWorkerThread = context.getBean(TaskWorkerThread.class);
        return taskWorkerThread;
    }
}
