package pascal.taie.analysis.pta.plugin.solar.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// TODO: Due to limitation of Tai-e framework, currently unsupported. Relevant function will be
//  implemented after Tai-e framework is updated.
@Target(ElementType.LOCAL_VARIABLE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ReflectiveMethod {
    public String[] candidates() default {};
}
