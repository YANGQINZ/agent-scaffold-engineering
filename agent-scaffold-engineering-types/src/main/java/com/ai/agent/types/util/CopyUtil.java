package com.ai.agent.types.util;

import cn.hutool.core.collection.CollUtil;
import com.ai.agent.types.util.cglib.CglibUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CopyUtil {

    public static <T> T copyBean(Object source, Supplier<T> supplier){
        return copyBean(source, supplier.get());
    }

    public static <T> T copyBean(Object source, T t){
        return Optional.ofNullable(source)
                .map(o -> {
                    CglibUtil.copy(o, t);
                    return t;
                }).orElse(null);
    }

    public static <T> List<T> copyList(Collection<?> source, Supplier<T> supplier){
        return copyCollection(source, supplier, ArrayList::new);
    }

    public static <T> Set<T> copySet(Collection<?> source, Supplier<T> supplier){
        return copyCollection(source, supplier, HashSet::new);
    }

    public static <T, C extends Collection<T>> C copyCollection(Collection<?> source, Supplier<T> beanSupplier, Supplier<C> collectionSupplier){
        return CollUtil.isEmpty(source) ? collectionSupplier.get()
                : source.stream().map(o -> copyBean(o, beanSupplier)).collect(Collectors.toCollection(collectionSupplier));
    }
}
