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
package illarion.download;

import javafx.application.Application;
import javafx.beans.Observable;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class JavaFxRuntimeTest {
    @DataProvider
    public Object[][] modules() {
        return new Object[][] {
            {Observable.class, "javafx.base"},
            {Application.class, "javafx.graphics"},
            {Button.class, "javafx.controls"},
            {FXMLLoader.class, "javafx.fxml"}
        };
    }

    @Test(dataProvider = "modules")
    public void javaFxLoadsFromNamedModules(Class<?> type, String moduleName) {
        assertEquals(type.getModule().getName(), moduleName,
                "JavaFX must load from the module path or linked runtime");
    }
}
