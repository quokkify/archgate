package dev.quokkify.archgate.context.archunit.support;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

public final class GeneratedCode {

    private static final String QUERYDSL_BEAN_PATH = "com.querydsl.core.types.dsl.BeanPath";

    public static final DescribedPredicate<JavaClass> EXCLUDED =
            JavaClass.Predicates.assignableTo(QUERYDSL_BEAN_PATH).as("generated QueryDSL classes");

    private GeneratedCode() {}
}
