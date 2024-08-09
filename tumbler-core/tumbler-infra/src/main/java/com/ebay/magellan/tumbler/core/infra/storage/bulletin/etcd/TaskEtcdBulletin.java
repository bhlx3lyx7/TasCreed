package com.ebay.magellan.tumbler.core.infra.storage.bulletin.etcd;

import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.PutOption;
import com.ebay.magellan.tumbler.core.domain.task.Task;
import com.ebay.magellan.tumbler.core.domain.task.TaskViews;
import com.ebay.magellan.tumbler.core.infra.constant.TumblerKeys;
import com.ebay.magellan.tumbler.core.infra.storage.bulletin.TaskBulletin;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerErrorEnum;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerException;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerExceptionBuilder;
import com.ebay.magellan.tumbler.depend.common.logger.TumblerLogger;
import com.ebay.magellan.tumbler.depend.ext.etcd.constant.EtcdConstants;
import com.ebay.magellan.tumbler.depend.ext.etcd.lock.EtcdLock;
import com.ebay.magellan.tumbler.depend.ext.etcd.util.EtcdUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class TaskEtcdBulletin extends BaseOccupyEtcdBulletin implements TaskBulletin {

    private static final String THIS_CLASS_NAME = TaskEtcdBulletin.class.getSimpleName();

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public TaskEtcdBulletin(TumblerKeys tumblerKeys,
                            EtcdConstants etcdConstants,
                            EtcdUtil etcdUtil,
                            TumblerLogger logger) {
        super(tumblerKeys, etcdConstants, etcdUtil, logger);
    }

    // -----

    public Map<String, String> readAllTodoTasks() throws Exception {
        return etcdUtil.getKVMapWithPrefix(tumblerKeys.buildTaskInfoTodoPrefix());
    }
    public Map<String, String> readAllDoneTasks() throws Exception {
        return etcdUtil.getKVMapWithPrefix(tumblerKeys.buildTaskInfoDonePrefix());
    }
    public Map<String, String> readAllErrorTasks() throws Exception {
        return etcdUtil.getKVMapWithPrefix(tumblerKeys.buildTaskInfoErrorPrefix());
    }

    // -----

    public String getTaskAdoptionKey(Task task) {
        if (task == null) return null;
        return tumblerKeys.getTaskAdoptionKey(task.getJobName(), task.getTrigger(), task.getTaskName());
    }

    public String checkTaskAdoption(Task task) throws TumblerException {
        try {
            String key = getTaskAdoptionKey(task);
            return etcdUtil.getSingleValue(key);
        } catch (Exception e) {
            TumblerExceptionBuilder.throwEtcdRetryableException(e);
        }
        return null;
    }

    public Map<String, String> readAllTaskAdoptions() throws Exception {
        return etcdUtil.getKVMapWithPrefix(tumblerKeys.buildTaskAdoptionPrefix());
    }

    // -----

    public boolean moveTodoTask2DoneTask(Task task, String adoptionValue,
                                         boolean withError) throws TumblerException {
        if (task == null) return false;

        String value = null;
        try {
            value = task.toJson(TaskViews.TASK_DONE.class);
        } catch (JsonProcessingException e) {
            TumblerExceptionBuilder.throwTumblerException(
                    TumblerErrorEnum.TUMBLER_FATAL_VALIDATION_EXCEPTION, e.getMessage());
        }
        if (StringUtils.isBlank(value)) return false;

        boolean success = false;

        String taskUpdateLock = tumblerKeys.getTaskUpdateLockKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        EtcdLock lock = null;

        try {
            lock = etcdUtil.lock(taskUpdateLock);
            success = moveTodoTask2DoneTaskImpl(task, value, adoptionValue, withError);
        } catch (TumblerException e) {
            throw e;
        } catch (Exception e) {
            TumblerExceptionBuilder.throwEtcdRetryableException(e);
        } finally {
            try {
                etcdUtil.unlock(lock);
            } catch (Exception e) {
                TumblerExceptionBuilder.throwEtcdRetryableException(e);
            }
        }

        return success;
    }

    boolean moveTodoTask2DoneTaskImpl(Task task, String doneTaskValue,
                                      String adoptionValue, boolean withError) throws Exception {
        if (task == null) return false;

        String todoTaskKey = tumblerKeys.getTodoTaskKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        String doneTaskKey = tumblerKeys.getDoneTaskKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        String errorTaskKey = tumblerKeys.getErrorTaskKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        String adoptionKey = getTaskAdoptionKey(task);

        Txn txn = etcdUtil.txn();

        // compare adoption
        if (StringUtils.isNotBlank(adoptionValue)) {
            Cmp cmp = new Cmp(bs(adoptionKey), Cmp.Op.EQUAL, CmpTarget.value(bs(adoptionValue)));
            txn.If(cmp);
        }

        // compare task info
        if (StringUtils.isNotBlank(task.getFromValue())) {
            Cmp cmp = new Cmp(bs(todoTaskKey), Cmp.Op.EQUAL, CmpTarget.value(bs(task.getFromValue())));
            txn.If(cmp);
        }

        // delete todoTask
        Op todoTaskDelOp = Op.delete(bs(todoTaskKey), DeleteOption.DEFAULT);
//        Op taskAdoptionDelOp = Op.delete(bs(adoptionKey), DeleteOption.DEFAULT);
//        txn.Then(todoTaskDelOp, taskAdoptionDelOp);
        txn.Then(todoTaskDelOp);

        // put doneTask
        Op doneTaskPutOp = Op.put(bs(doneTaskKey), bs(doneTaskValue), PutOption.DEFAULT);
        txn.Then(doneTaskPutOp);

        // put errorTask
        if (withError) {
            Op errorTaskPutOp = Op.put(bs(errorTaskKey), bs(doneTaskValue), PutOption.DEFAULT);
            txn.Then(errorTaskPutOp);
        }

        TxnResponse txnResponse = txn.commit()
                .get(etcdConstants.getEtcdTimeoutInSeconds(), TimeUnit.SECONDS);
        boolean success = CollectionUtils.isNotEmpty(txnResponse.getPutResponses());

        // update task from value
        if (success) {
            task.setFromValue(doneTaskValue);
        }

        return success;
    }

    // -----

    public boolean updateTodoTask(Task task, String adoptionValue) throws TumblerException {
        if (task == null) return false;

        String value = null;
        try {
            value = task.toJson(TaskViews.TASK_TODO.class);
        } catch (JsonProcessingException e) {
            TumblerExceptionBuilder.throwTumblerException(
                    TumblerErrorEnum.TUMBLER_FATAL_VALIDATION_EXCEPTION, e.getMessage());
        }
        if (StringUtils.isBlank(value)) return false;

        boolean success = false;

        String taskUpdateLock = tumblerKeys.getTaskUpdateLockKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        EtcdLock lock = null;

        try {
            lock = etcdUtil.lock(taskUpdateLock);
            success = updateTodoTaskImpl(task, value, adoptionValue);
        } catch (TumblerException e) {
            throw e;
        } catch (Exception e) {
            TumblerExceptionBuilder.throwEtcdRetryableException(e);
        } finally {
            try {
                etcdUtil.unlock(lock);
            } catch (Exception e) {
                TumblerExceptionBuilder.throwEtcdRetryableException(e);
            }
        }

        return success;
    }

    boolean updateTodoTaskImpl(Task task, String todoTaskValue, String adoptionValue) throws Exception {
        if (task == null) return false;

        String todoTaskKey = tumblerKeys.getTodoTaskKey(task.getJobName(), task.getTrigger(), task.getTaskName());
        String adoptionKey = getTaskAdoptionKey(task);

        Txn txn = etcdUtil.txn();

        // compare adoption
        if (StringUtils.isNotBlank(adoptionValue)) {
            Cmp cmp = new Cmp(bs(adoptionKey), Cmp.Op.EQUAL, CmpTarget.value(bs(adoptionValue)));
            txn.If(cmp);
        }

        // compare task info
        if (StringUtils.isNotBlank(task.getFromValue())) {
            Cmp cmp = new Cmp(bs(todoTaskKey), Cmp.Op.EQUAL, CmpTarget.value(bs(task.getFromValue())));
            txn.If(cmp);
        }

        // put todoTask
        Op todoTaskPutOp = Op.put(bs(todoTaskKey), bs(todoTaskValue), PutOption.DEFAULT);
        txn.Then(todoTaskPutOp);

        TxnResponse txnResponse = txn.commit()
                .get(etcdConstants.getEtcdTimeoutInSeconds(), TimeUnit.SECONDS);
        boolean success = CollectionUtils.isNotEmpty(txnResponse.getPutResponses());

        // update task from value
        if (success) {
            task.setFromValue(todoTaskValue);
        }

        return success;
    }

    // -----
}
