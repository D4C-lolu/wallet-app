package com.interswitch.walletapp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//TODO: Refactor this
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DisableFraudDetection {
}