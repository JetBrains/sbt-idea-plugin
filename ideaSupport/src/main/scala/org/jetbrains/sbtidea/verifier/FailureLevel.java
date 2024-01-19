package org.jetbrains.sbtidea.verifier;

import java.util.EnumSet;

public enum FailureLevel {
    COMPATIBILITY_WARNINGS("Compatibility warnings"),
    COMPATIBILITY_PROBLEMS("Compatibility problems"),
    DEPRECATED_API_USAGES("Deprecated API usages"),
    EXPERIMENTAL_API_USAGES("Experimental API usages"),
    INTERNAL_API_USAGES("Internal API usages"),
    OVERRIDE_ONLY_API_USAGES("Override-only API usages"),
    NON_EXTENDABLE_API_USAGES("Non-extendable API usages"),
    PLUGIN_STRUCTURE_WARNINGS("Plugin structure warnings"),
    MISSING_DEPENDENCIES("Missing dependencies"),
    INVALID_PLUGIN("The following files specified for the verification are not valid plugins"),
    NOT_DYNAMIC("Plugin cannot be loaded/unloaded without IDE restart");

    @SuppressWarnings("unused") //can be used by sbt plugin users
    public static final EnumSet<FailureLevel> ALL = EnumSet.allOf(FailureLevel.class);
    @SuppressWarnings("unused") //can be used by sbt plugin users
    public static final EnumSet<FailureLevel> NONE = EnumSet.noneOf(FailureLevel.class);

    public final String testValue;

    FailureLevel(String testValue) {
        this.testValue = testValue;
    }
}