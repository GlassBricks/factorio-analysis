package glassbricks.recipeanalysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Needed as a java file since Kotlin tries to override the boxing version of [put] instead
 */
public final class ZeroPutOpenHashMap<K> extends Object2DoubleOpenHashMap<K> implements ZeroPutMap<K> {
    public ZeroPutOpenHashMap(int size) {
        super(size);
    }

    public ZeroPutOpenHashMap() {
        super();
    }

    @Override
    public double put(K key, double value) {
        if (value == 0) return removeDouble(key);
        return super.put(key, value);
    }

    @Override
    public @NotNull Iterator<@NotNull Entry<K>> iterator() {
        return object2DoubleEntrySet().fastIterator();
    }
}
