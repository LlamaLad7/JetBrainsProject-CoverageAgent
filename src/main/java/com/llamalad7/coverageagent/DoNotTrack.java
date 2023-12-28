package com.llamalad7.coverageagent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Any methods annotated with this will not count towards coverage.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface DoNotTrack {
}
