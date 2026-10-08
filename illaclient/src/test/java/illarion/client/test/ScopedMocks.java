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

import org.mockito.ScopedMock;
import org.testng.annotations.AfterMethod;

import java.util.ArrayDeque;
import java.util.Deque;

/** Closes thread-local static and construction mocks, including after failed setup. */
public abstract class ScopedMocks {
    private final Deque<ScopedMock> mocks = new ArrayDeque<>();

    protected <T extends ScopedMock> T scoped(T mock) {
        mocks.push(mock);
        return mock;
    }

    @AfterMethod(alwaysRun = true)
    public final void closeScopedMocks() {
        while (!mocks.isEmpty()) {
            mocks.pop().closeOnDemand();
        }
    }
}
