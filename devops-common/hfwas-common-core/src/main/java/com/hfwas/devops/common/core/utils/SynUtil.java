package com.hfwas.devops.common.core.utils;

import org.apache.commons.collections.MapUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author houfei
 * @package com.hfwas.devops.common.core.utils
 * @date 2025/1/15
 */
public class SynUtil {

    /**
     * 这个函数会以 newList 为基准，对 oldList 中的差异元素进行增加，删除，与更新的操作
     * 注意：
     * 1. oldList, newList 须将元素 List 转换为 Map 传入，将元素的主键作为 Key
     * 2. 元素须实现 equals 方法
     */
    public static <K, V> void compareAndUpdate(Map<K, V> oldList, Map<K, V> newList, Consumer<V> addFun,
                                               BiConsumer<V, V> updateFun, Consumer<V> deleteFun) {
        Objects.requireNonNull(addFun);
        Objects.requireNonNull(updateFun);
        Objects.requireNonNull(deleteFun);

        if (MapUtils.isEmpty(oldList) && MapUtils.isEmpty(newList)) {
            return;
        }

        if (MapUtils.isEmpty(oldList)) {
            newList.values().forEach(addFun);
        } else if (MapUtils.isEmpty(newList)) {
            oldList.values().forEach(deleteFun);
        } else {
            oldList.forEach((oldK, oldV) -> {
                if (newList.containsKey(oldK)) {
                    V newV = newList.get(oldK);
                    if (!oldV.equals(newV)) {
                        updateFun.accept(oldV, newV);
                    }
                    newList.remove(oldK);
                } else {
                    deleteFun.accept(oldV);
                }
            });
            newList.values().forEach(addFun);
        }
    }

}
