package com.ebay.magellan.tumbler.depend.common.util;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class ExceptionUtil {
    public static String getStackTrace(Throwable e) {
        return ExceptionUtils.getStackTrace(e);
    }
}
