package archfixtures.generated;

import com.querydsl.core.types.dsl.BeanPath;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
public class QFoo extends BeanPath<Object> {

    public QFoo() {
        super(Object.class, "foo");
    }

    public void print() {
        System.out.println("generated");
    }
}
