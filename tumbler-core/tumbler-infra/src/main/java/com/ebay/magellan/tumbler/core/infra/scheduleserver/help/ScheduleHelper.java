package com.ebay.magellan.tumbler.core.infra.scheduleserver.help;

import com.ebay.magellan.tumbler.core.domain.job.Job;
import com.ebay.magellan.tumbler.core.domain.schedule.Schedule;
import com.ebay.magellan.tumbler.core.domain.util.JsonUtil;
import com.ebay.magellan.tumbler.core.infra.constant.TumblerKeys;
import com.ebay.magellan.tumbler.core.infra.storage.bulletin.ScheduleBulletin;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerErrorEnum;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerException;
import com.ebay.magellan.tumbler.depend.common.exception.TumblerExceptionBuilder;
import com.ebay.magellan.tumbler.depend.ext.etcd.lock.EtcdLock;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class ScheduleHelper {

    @Autowired
    private TumblerKeys tumblerKeys;

    @Autowired
    private ScheduleBulletin scheduleBulletin;

    public boolean submitScheduleWithJobs(Schedule schedule, List<Job> newJobs) throws TumblerException {
        boolean ret = false;
        if (schedule == null) return ret;

        schedule.getMidState().setModifyTime(new Date());

        String scheduleUpdateLock = tumblerKeys.getScheduleUpdateLockKey(schedule.getScheduleName());
        EtcdLock lock = null;
        try {
            lock = scheduleBulletin.lock(scheduleUpdateLock);

            ret = scheduleBulletin.submitScheduleAndJobs(schedule, newJobs);

        } catch (JsonProcessingException e) {
            TumblerExceptionBuilder.throwTumblerException(
                    TumblerErrorEnum.TUMBLER_FATAL_VALIDATION_EXCEPTION, e.getMessage());
        } catch (Exception e) {
            TumblerExceptionBuilder.throwTumblerException(
                    TumblerErrorEnum.TUMBLER_RETRY_EXCEPTION, e.getMessage());
        } finally {
            try {
                scheduleBulletin.unlock(lock);
            } catch (Exception e) {
                TumblerExceptionBuilder.throwTumblerException(
                        TumblerErrorEnum.TUMBLER_RETRY_EXCEPTION, e.getMessage());
            }
        }
        return ret;
    }

    // -----

    public List<Schedule> readAllSchedules() throws TumblerException {
        List<Schedule> ret = new ArrayList<>();
        try {
            Map<String, String> pairs = scheduleBulletin.readAllSchedules();
            ret = JsonUtil.parseSchedules(pairs.values());
        } catch (Exception e) {
            TumblerExceptionBuilder.throwEtcdRetryableException(e);
        }
        return ret;
    }

    public Schedule readSchedule(String scheduleName) {
        if (StringUtils.isBlank(scheduleName)) return null;
        return JsonUtil.parseSchedule(scheduleBulletin.readSchedule(scheduleName));
    }

    // -----

    public Schedule deleteSchedule(String scheduleName) throws Exception {
        if (StringUtils.isBlank(scheduleName)) return null;
        return JsonUtil.parseSchedule(scheduleBulletin.deleteSchedule(scheduleName));
    }

}
