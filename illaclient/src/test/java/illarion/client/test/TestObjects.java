/*
 * This file is part of the Illarion project.
 *
 * Illarion is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Illarion is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */
package illarion.client.test;

import org.objenesis.ObjenesisStd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Builds real legacy fixtures without graphics constructors or a custom class loader. */
public final class TestObjects {
    private TestObjects() {
    }

    public static <T> T newInstance(Class<T> type) {
        return new ObjenesisStd().newInstance(type);
    }

    public static void setInternalState(Object target, String name, Object value) {
        try {
            field(target.getClass(), name).set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot initialise fixture field " + name, exception);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getInternalState(Object target, String name) {
        try {
            return (T) field(target.getClass(), name).get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot read fixture field " + name, exception);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Legacy fields may be declared by the superclass.
            }
        }

        throw new NoSuchFieldException(name);
    }

    public static void invokeMethod(Object target, String name) throws ReflectiveOperationException {
        invokeMethod(target, name, new Class<?>[0]);
    }

    public static void invokeMethod(Object target, String name, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, arguments);
    }
}
