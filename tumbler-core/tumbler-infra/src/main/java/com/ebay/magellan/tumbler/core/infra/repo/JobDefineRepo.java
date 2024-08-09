package com.ebay.magellan.tumbler.core.infra.repo;

import com.ebay.magellan.tumbler.core.domain.define.JobDefine;
import com.ebay.magellan.tumbler.core.infra.repo.read.JobDefineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JobDefineRepo extends DefineRepo<JobDefine> {
    public JobDefineRepo(@Autowired JobDefineReader reader) {
        super(reader, JobDefine.class);
    }
}
