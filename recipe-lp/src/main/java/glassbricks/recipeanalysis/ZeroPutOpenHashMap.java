package glassbricks.recipeanalysis;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public final class ZeroPutOpenHashMap<T> extends Object2DoubleOpenHashMap<T> implements ZeroPutMap<T> {
    public ZeroPutOpenHashMap(int size) {
        super(size);
    }

    public ZeroPutOpenHashMap() {
        super();
    }

    @Override
    public double put(T key, double value) {
        if (value == 0) return removeDouble(key);
        return super.put(key, value);
    }

    @Override
    public @NotNull Iterator<@NotNull Entry<T>> iterator() {
        return object2DoubleEntrySet().fastIterator();
    }
}
