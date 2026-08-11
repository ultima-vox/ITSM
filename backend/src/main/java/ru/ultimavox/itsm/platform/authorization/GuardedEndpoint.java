package ru.ultimavox.itsm.platform.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks API adapters guarded through a dedicated policy gateway instead of direct AccessControl. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GuardedEndpoint {}
