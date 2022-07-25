package com.fynd.extension.model;

import com.fynd.extension.session.Session;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.function.Function;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionCallback {

    private Function<HttpServletRequest,String> auth;

    private Function<HttpServletRequest,String> install;

    private Function<HttpServletRequest,String> uninstall;

    private Function<HttpServletRequest,String> autoInstall;






}
