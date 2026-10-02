/*
 * This file is part of the Illarion project.
 *
 * Copyright © 2026 - Illarion e.V.
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
package illarion.client.gui.controller.game;

import de.lessvoid.nifty.Nifty;
import de.lessvoid.nifty.builder.EffectBuilder;
import de.lessvoid.nifty.builder.LayerBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.builder.ScreenBuilder;
import de.lessvoid.nifty.controls.window.WindowControl;
import de.lessvoid.nifty.effects.EffectEventId;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.input.InputSystem;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.sound.SoundDevice;
import illarion.client.gui.DialogGui;
import illarion.client.gui.DialogType;
import illarion.client.gui.GameGui;
import illarion.client.net.NetComm;
import illarion.client.net.client.CloseDialogCraftingCmd;
import illarion.client.util.UpdateTaskManager;
import illarion.client.world.World;
import illarion.client.world.items.CraftingItem;
import org.easymock.EasyMock;
import org.illarion.engine.GameContainer;
import org.illarion.nifty.controls.CraftingItemEntry;
import org.illarion.nifty.controls.DialogCrafting;
import org.illarion.nifty.controls.DialogCraftingCloseEvent;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockObjectFactory;
import org.powermock.reflect.Whitebox;
import org.testng.IObjectFactory;
import org.testng.annotations.ObjectFactory;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.EnumSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@PrepareForTest({World.class, NetComm.class})
@PowerMockIgnore({"javax.management.*", "javax.xml.parsers.*", "com.sun.org.apache.xerces.internal.jaxp.*",
        "ch.qos.logback.*", "org.slf4j.*", "de.lessvoid.nifty.*"})
public class DialogHandlerTest {
    private DialogHandler handler;
    private UpdateTaskManager updates;
    private GameContainer container;
    private Nifty nifty;
    private Element element;
    private DialogCrafting crafting;
    private NetComm network;
    private int dialogId;
    private int selectedIndex;
    private int amount;
    private float progress;
    private long time;

    @ObjectFactory
    public IObjectFactory createObjectFactory() {
        return new PowerMockObjectFactory();
    }

    private void setUp(boolean hideEffect) {
        RenderDevice render = EasyMock.createNiceMock(RenderDevice.class);
        EasyMock.expect(render.getWidth()).andReturn(800).anyTimes();
        EasyMock.expect(render.getHeight()).andReturn(600).anyTimes();
        SoundDevice sound = EasyMock.createNiceMock(SoundDevice.class);
        InputSystem input = EasyMock.createNiceMock(InputSystem.class);
        container = EasyMock.createNiceMock(GameContainer.class);
        ScreenController controller = EasyMock.createNiceMock(ScreenController.class);
        EasyMock.replay(render, sound, input, container, controller);

        time = 1000;
        nifty = new Nifty(render, sound, input, () -> time);
        ScreenBuilder builder = new ScreenBuilder("game", controller);
        LayerBuilder layer = new LayerBuilder("windows");
        layer.childLayoutCenter();
        PanelBuilder panel = new PanelBuilder("craftingDialog");
        panel.childLayoutCenter();
        panel.width("100px");
        panel.height("100px");
        panel.visible(false);

        if (hideEffect) {
            EffectBuilder fade = new EffectBuilder("fade");
            fade.length(200);
            fade.effectParameter("start", "#f");
            fade.effectParameter("end", "#0");
            panel.onHideEffect(fade);
        }

        layer.panel(panel);
        builder.layer(layer);
        Screen screen = builder.build(nifty);
        nifty.addScreen("game", screen);
        nifty.gotoScreen("game");
        element = screen.findElementById("craftingDialog");
        assertFalse(element.isVisible());

        // Keep Nifty's real visibility, hide effects and WindowClosedEvent behaviour.
        WindowControl window = new WindowControl();
        Whitebox.setInternalState(window, "element", element);
        Whitebox.setInternalState(window, "nifty", nifty);
        Whitebox.setInternalState(window, "hideOnClose", true);
        crafting = EasyMock.createNiceMock(DialogCrafting.class);
        dialogId = 0;
        selectedIndex = 0;
        amount = 0;
        progress = 0;
        EasyMock.expect(crafting.getDialogId()).andAnswer(() -> dialogId).anyTimes();
        crafting.setDialogId(EasyMock.anyInt());
        EasyMock.expectLastCall().andAnswer(() -> {
            dialogId = (Integer) EasyMock.getCurrentArguments()[0];
            return null;
        }).anyTimes();
        EasyMock.expect(crafting.getElement()).andReturn(element).anyTimes();
        crafting.closeWindow();
        EasyMock.expectLastCall().andAnswer(() -> {
            window.closeWindow();
            return null;
        }).anyTimes();
        crafting.selectItemByItemIndex(EasyMock.anyInt());
        EasyMock.expectLastCall().andAnswer(() -> {
            selectedIndex = (Integer) EasyMock.getCurrentArguments()[0];
            return null;
        }).anyTimes();
        crafting.setAmount(EasyMock.anyInt());
        EasyMock.expectLastCall().andAnswer(() -> {
            amount = (Integer) EasyMock.getCurrentArguments()[0];
            return null;
        }).anyTimes();
        crafting.setProgress(EasyMock.anyFloat());
        EasyMock.expectLastCall().andAnswer(() -> {
            progress = (Float) EasyMock.getCurrentArguments()[0];
            return null;
        }).anyTimes();

        handler = new DialogHandler(null, null, null);
        Whitebox.setInternalState(handler, "nifty", nifty);
        Whitebox.setInternalState(handler, "screen", screen);
        Whitebox.setInternalState(handler, "craftingDialog", crafting);
        updates = new UpdateTaskManager();
        network = PowerMock.createMock(NetComm.class);
        GameGui gui = EasyMock.createMock(GameGui.class);
        EasyMock.expect(gui.getDialogGui()).andReturn(handler).anyTimes();
        EasyMock.replay(gui);
        PowerMock.mockStatic(World.class);
        EasyMock.expect(World.getUpdateTaskManager()).andReturn(updates).anyTimes();
        EasyMock.expect(World.getNet()).andReturn(network).anyTimes();
        EasyMock.expect(World.getGameGui()).andReturn(gui).anyTimes();
        PowerMock.replay(World.class);
    }

    private void start() {
        EasyMock.replay(crafting);
        PowerMock.replay(network);
        open(0);
        frame();
        assertTrue(element.isVisible());
    }

    private void open(int id) {
        handler.showCraftingDialog(id, "Gemcutting", Collections.singletonList("Gems"),
                Collections.<CraftingItem>emptyList());
    }

    private void close(int id) {
        handler.closeDialog(id, EnumSet.of(DialogType.Crafting));
    }

    private void frame() {
        // PlayingState processes the general GUI tasks before the child handlers.
        updates.onUpdateGame(container, 16);
        handler.update(container, 16);
    }

    @Test
    public void testFirstOpenShowsWindow() {
        setUp(false);
        start();
        assertEquals(amount, 1);
        PowerMock.verify(network);
    }

    @Test
    public void testReusedIdAfterServerCloseShowsWindow() {
        setUp(false);
        start();
        close(0);
        frame();
        assertFalse(element.isVisible());
        open(0);
        frame();
        assertTrue(element.isVisible());
        PowerMock.verify(network);
    }

    @Test
    public void testNewIdAfterServerCloseShowsWindow() {
        setUp(false);
        start();
        close(0);
        frame();
        open(1);
        frame();
        assertTrue(element.isVisible());
        assertEquals(dialogId, 1);
    }

    @Test
    public void testServerCloseEndsProduction() {
        setUp(false);
        start();
        handler.startProductionIndicator(0, 3, 20);
        frame();
        assertTrue(handler.isCraftingInProgress());
        close(0);
        frame();
        assertFalse(handler.isCraftingInProgress());
        PowerMock.verify(network);
    }

    @Test
    public void testCloseAllAllowsReopening() {
        setUp(false);
        start();
        handler.closeDialog(DialogGui.ALL_DIALOGS, EnumSet.allOf(DialogType.class));
        frame();
        assertFalse(element.isVisible());
        open(0);
        frame();
        assertTrue(element.isVisible());
    }

    @Test
    public void testNewDialogDoesNotInheritProduction() {
        setUp(false);
        start();
        handler.startProductionIndicator(0, 3, 20);
        frame();
        open(1);
        frame();
        assertFalse(handler.isCraftingInProgress());
        assertEquals(amount, 1);
    }

    @Test
    public void testOtherIdDoesNotCloseCrafting() {
        setUp(false);
        start();
        close(1);
        frame();
        assertTrue(element.isVisible());
    }

    @Test
    public void testOtherTypeDoesNotCloseCrafting() {
        setUp(false);
        start();
        handler.closeDialog(0, EnumSet.of(DialogType.Message));
        frame();
        assertTrue(element.isVisible());
    }

    @Test
    public void testOpenDialogUpdatePreservesSelectionAndProduction() {
        setUp(false);
        CraftingItemEntry selection = EasyMock.createMock(CraftingItemEntry.class);
        EasyMock.expect(selection.getItemIndex()).andReturn(7).anyTimes();
        EasyMock.replay(selection);
        EasyMock.expect(crafting.getSelectedCraftingItem()).andReturn(selection).anyTimes();
        start();
        handler.startProductionIndicator(0, 3, 20);
        frame();
        progress = 0.5f;
        open(0);
        frame();
        assertEquals(selectedIndex, 7);
        assertEquals(amount, 3);
        assertEquals(progress, 0.5f);
        assertTrue(handler.isCraftingInProgress());
        assertTrue(element.isVisible());
    }

    @Test
    public void testManualCloseSendsOneReplyAndAllowsReopening() {
        setUp(false);
        network.sendCommand(EasyMock.isA(CloseDialogCraftingCmd.class));
        EasyMock.expectLastCall().once();
        start();
        handler.handleCraftingCloseDialogEvent("craftingDialog", new DialogCraftingCloseEvent(0));
        handler.handleCraftingCloseDialogEvent("craftingDialog", new DialogCraftingCloseEvent(0));
        frame();
        assertFalse(element.isVisible());
        open(0);
        frame();
        assertTrue(element.isVisible());
        PowerMock.verify(network);
    }

    @Test
    public void testScreenEndAllowsSameIdInNextSession() {
        setUp(false);
        start();
        handler.onEndScreen();
        assertFalse(element.isVisible());
        open(0);
        frame();
        assertTrue(element.isVisible());
        PowerMock.verify(network);
    }

    @Test
    public void testReopenCancelsPendingHideEffect() {
        setUp(true);
        start();
        close(0);
        frame();
        assertTrue(element.isEffectActive(EffectEventId.onHide));
        open(0);
        frame();
        assertFalse(element.isEffectActive(EffectEventId.onHide));
        time += 1000;
        nifty.update();
        nifty.render(false);
        assertTrue(element.isVisible());
    }
}
