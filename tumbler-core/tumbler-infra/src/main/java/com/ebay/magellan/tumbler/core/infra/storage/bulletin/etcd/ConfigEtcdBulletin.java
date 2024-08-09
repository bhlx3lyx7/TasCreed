package com.ebay.magellan.tumbler.core.infra.storage.bulletin.etcd;

import com.ebay.magellan.tumbler.core.infra.constant.TumblerKeys;
import com.ebay.magellan.tumbler.core.infra.storage.bulletin.ConfigBulletin;
import com.ebay.magellan.tumbler.depend.common.logger.TumblerLogger;
import com.ebay.magellan.tumbler.depend.ext.etcd.constant.EtcdConstants;
import com.ebay.magellan.tumbler.depend.ext.etcd.util.EtcdUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ConfigEtcdBulletin extends BaseEtcdBulletin implements ConfigBulletin {

    private static final String THIS_CLASS_NAME = ConfigEtcdBulletin.class.getSimpleName();

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public ConfigEtcdBulletin(TumblerKeys tumblerKeys,
                              EtcdConstants etcdConstants,
                              EtcdUtil etcdUtil,
                              TumblerLogger logger) {
        super(tumblerKeys, etcdConstants, etcdUtil, logger);
    }

    // -----

    public String readConfig(String key) {
        return etcdUtil.getSingleValue(key);
    }
    public String readConfig(String key, String defaultValue) {
        return etcdUtil.getSingleValueOrDefault(key, defaultValue);
    }

    public Map<String, String> readConfigs(String prefix) throws Exception {
        return etcdUtil.getKVMapWithPrefix(prefix);
    }

    // -----

    public void updateConfig(String key, String value) throws Exception {
        etcdUtil.putKeyValue(key, value);
    }

}
