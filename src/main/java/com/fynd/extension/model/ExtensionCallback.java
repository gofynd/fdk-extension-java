package com.fynd.extension.model;

import com.fynd.extension.session.Session;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;
import java.util.function.Function;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtensionCallback {

    private Function<Session ,String> auth;

    private Function<Session,String> install;

    private Function<Session,String> uninstall;






}
