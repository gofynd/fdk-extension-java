package com.fynd.extension.model;

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

    private Function<Map<String,Object>,String> auth;

    private Function<Map<String,Object>,String> install;

    private Function<Map<String,Object>,String> uninstall;






}
