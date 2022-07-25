package com.fynd.extension.middleware;

import java.text.SimpleDateFormat;

public class FdkConstants {

    public static final String SESSION_COOKIE_NAME = "ext_session";

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        }
    };
}
