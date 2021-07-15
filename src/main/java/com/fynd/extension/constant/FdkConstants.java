package com.fynd.extension.constant;

import java.text.SimpleDateFormat;

public class FdkConstants {

    public static final String SESSION_COOKIE_NAME = "ext_session";

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("uuuu-MM-dd'T'HH:mm:ssXXX");
        }
    };
}
