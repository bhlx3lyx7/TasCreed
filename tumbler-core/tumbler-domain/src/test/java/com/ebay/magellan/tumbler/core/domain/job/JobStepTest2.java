package com.ebay.magellan.tumbler.core.domain.job;

import com.ebay.magellan.tumbler.core.domain.builder.JobBuilder;
import com.ebay.magellan.tumbler.core.domain.builder.TaskBuilder;
import com.ebay.magellan.tumbler.core.domain.help.TestRepo;
import com.ebay.magellan.tumbler.core.domain.state.StepStateEnum;
import com.ebay.magellan.tumbler.core.domain.state.TaskStateEnum;
import com.ebay.magellan.tumbler.core.domain.task.Task;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class JobStepTest2 {

    JobBuilder jobBuilder = new JobBuilder();
    TaskBuilder taskBuilder = new TaskBuilder();

    @Test
    public void updateJobStepStateByTaskStates1() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }

        step.updateStepStateByTaskStates();
        assertEquals(StepStateEnum.SUCCESS, step.getState());
    }

    @Test
    public void updateJobStepStateByTaskStates2() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }
        step.updateTaskState(tasks.get(0).getTaskAllConf(), TaskStateEnum.ERROR);

        step.updateStepStateByTaskStates();
        assertEquals(StepStateEnum.ERROR, step.getState());
    }

    @Test
    public void updateJobStepStateByTaskStates3() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }
        step.updateTaskState(tasks.get(0).getTaskAllConf(), TaskStateEnum.UNDONE);

        step.updateStepStateByTaskStates();
        assertEquals(StepStateEnum.READY, step.getState());
    }

    @Test
    public void updateJobStepStateByTaskStates4() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }
        step.updateTaskState(tasks.get(0).getTaskAllConf(), TaskStateEnum.FAILED);

        step.updateStepStateByTaskStates();
        assertEquals(StepStateEnum.FAILED, step.getState());
    }

    @Test
    public void refreshTaskStates() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }

        assertEquals(4, step.getTaskStates().size());
        step.refreshTaskStates();
        assertEquals(0, step.getTaskStates().size());
    }

    // -----

    @Test
    public void updateProgression() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);
        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);

        step.updateProgression();
        assertEquals("0.00%", step.getProgression().getValue());

        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
        }

        step.updateProgression();
        assertEquals("100.00%", step.getProgression().getValue());
    }

    // -----

    @Test
    public void updateStepAllDoneRange() {
        Job job = jobBuilder.buildJob(TestRepo.jobDefine, TestRepo.jobRequest2);

        JobStep step = job.getSteps().get(0);
        List<Task> tasks = taskBuilder.buildNewTasks(job);
        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
            if (task.getTaskAllConf().getShardConf().getIndex() % 3 != 1) {
                step.updateStepAllDoneRange(task.getTaskAllConf());
            }
        }
        step.updateStepStateByTaskStates();
        assertEquals("[0,0];[2,3]", step.getStepAllDoneRange().shard().getIndexRangesStr());

        step = job.getSteps().get(1);
        tasks = taskBuilder.buildNewTasks(job);
        for (Task task : tasks) {
            step.updateTaskState(task.getTaskAllConf(), TaskStateEnum.SUCCESS);
            if (task.getTaskAllConf().getPackConf().getId() % 3 != 1) {
                step.updateStepAllDoneRange(task.getTaskAllConf());
            }
        }
        step.updateStepStateByTaskStates();
        assertEquals("[0,99];[200,399];[500,599]", step.getStepAllDoneRange().pack().getOffsetRangesStr());
    }

}
