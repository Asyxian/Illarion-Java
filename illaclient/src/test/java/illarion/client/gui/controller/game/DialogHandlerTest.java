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
import de.lessvoid.nifty.builder.ControlDefinitionBuilder;
import de.lessvoid.nifty.builder.EffectBuilder;
import de.lessvoid.nifty.builder.LayerBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.builder.ScreenBuilder;
import de.lessvoid.nifty.controls.Window;
import de.lessvoid.nifty.effects.EffectEventId;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.input.InputSystem;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.sound.SoundDevice;
import illarion.client.graphics.FontLoader;
import illarion.client.gui.DialogGui;
import illarion.client.gui.DialogType;
import illarion.client.gui.GameGui;
import illarion.client.net.NetComm;
import illarion.client.net.client.CloseDialogCraftingCmd;
import illarion.client.test.ScopedMocks;
import illarion.client.test.TestObjects;
import illarion.client.util.UpdateTaskManager;
import illarion.client.world.World;
import illarion.client.world.items.CraftingItem;
import org.illarion.engine.GameContainer;
import org.illarion.engine.graphic.Font;
import org.illarion.nifty.controls.CraftingItemEntry;
import org.illarion.nifty.controls.DialogCrafting;
import org.illarion.nifty.controls.DialogCraftingCloseEvent;
import org.illarion.nifty.controls.DialogMerchant;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.EnumSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class DialogHandlerTest extends ScopedMocks {
    private MockedStatic<World> world;
    private MockedStatic<FontLoader> fontLoader;
    private DialogHandler handler;
    private UpdateTaskManager updates;
    private GameContainer container;
    private Nifty nifty;
    private Element element;
    private Element merchantElement;
    private Screen screen;
    private DialogCrafting crafting;
    private NetComm network;
    private int dialogId;
    private int selectedIndex;
    private int amount;
    private float progress;
    private long time;

    @AfterMethod(alwaysRun = true)
    public void noUnexpectedNetworkCommands() {
        if (network != null) {
            Mockito.verifyNoMoreInteractions(network);
        }
    }

    private void setUp(boolean hideEffect) {
        createScreen(hideEffect);
        prepareCraftingControl();
        handler = new DialogHandler(null, null, null);
        TestObjects.setInternalState(handler, "nifty", nifty);
        TestObjects.setInternalState(handler, "screen", screen);
        TestObjects.setInternalState(handler, "craftingDialog", crafting);
        prepareMerchantControl();
        registerPlaceholderDialogs();
        prepareWorld();
    }

    private void createScreen(boolean hideEffect) {
        RenderDevice render = Mockito.mock(RenderDevice.class);
        Mockito.when(render.getWidth()).thenReturn(800);
        Mockito.when(render.getHeight()).thenReturn(600);
        SoundDevice sound = Mockito.mock(SoundDevice.class);
        InputSystem input = Mockito.mock(InputSystem.class);
        container = Mockito.mock(GameContainer.class);
        ScreenController controller = Mockito.mock(ScreenController.class);

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
        PanelBuilder merchantPanel = new PanelBuilder("merchantDialog");
        merchantPanel.width("100px");
        merchantPanel.height("100px");
        merchantPanel.visible(false);
        layer.panel(merchantPanel);
        screen = builder.build(nifty);
        nifty.addScreen("game", screen);
        nifty.gotoScreen("game");
        element = screen.findElementById("craftingDialog");
        assertFalse(element.isVisible());
    }

    private void prepareCraftingControl() {
        Window window = createWindow(element);
        crafting = Mockito.mock(DialogCrafting.class);
        dialogId = 0;
        selectedIndex = 0;
        amount = 0;
        progress = 0;
        Mockito.when(crafting.getDialogId()).thenAnswer(invocation -> dialogId);
        Mockito.doAnswer(invocation -> {
            dialogId = (Integer) invocation.getArguments()[0];
            return null;
        }).when(crafting).setDialogId(Mockito.anyInt());
        Mockito.when(crafting.getElement()).thenReturn(element);
        Mockito.doAnswer(invocation -> {
            window.closeWindow();
            return null;
        }).when(crafting).closeWindow();
        Mockito.doAnswer(invocation -> {
            selectedIndex = (Integer) invocation.getArguments()[0];
            return null;
        }).when(crafting).selectItemByItemIndex(Mockito.anyInt());
        Mockito.doAnswer(invocation -> {
            amount = (Integer) invocation.getArguments()[0];
            return null;
        }).when(crafting).setAmount(Mockito.anyInt());
        Mockito.doAnswer(invocation -> {
            progress = (Float) invocation.getArguments()[0];
            return null;
        }).when(crafting).setProgress(Mockito.anyFloat());
    }

    private void prepareMerchantControl() {
        merchantElement = screen.findElementById("merchantDialog");
        Window merchantWindow = createWindow(merchantElement);
        DialogMerchant merchant = Mockito.mock(DialogMerchant.class);
        Mockito.when(merchant.getElement()).thenReturn(merchantElement);
        Mockito.when(merchant.getDialogId()).thenReturn(0);
        Mockito.doAnswer(invocation -> {
            merchantWindow.closeWindow();
            return null;
        }).when(merchant).closeWindow();

        TestObjects.setInternalState(handler, "merchantDialog", merchant);
    }

    // WindowControl is the existing production superclass; exercise its real close behaviour.
    @SuppressWarnings("deprecation")
    private Window createWindow(Element target) {
        Window window = new de.lessvoid.nifty.controls.window.WindowControl();
        TestObjects.setInternalState(window, "element", target);
        TestObjects.setInternalState(window, "nifty", nifty);
        TestObjects.setInternalState(window, "hideOnClose", true);
        return window;
    }

    private void registerPlaceholderDialogs() {
        // Minimal windows isolate ordering from resource-dependent dialog contents.
        for (String name : new String[] {"dialog-message", "dialog-input", "dialog-select"}) {
            ControlDefinitionBuilder definition = new ControlDefinitionBuilder(name);
            PanelBuilder root = new PanelBuilder();
            root.width("100px");
            root.height("100px");
            root.childLayoutCenter();
            definition.panel(root);
            definition.registerControlDefintion(nifty);
        }
    }

    private void prepareWorld() {
        Font font = Mockito.mock(Font.class);

        FontLoader fonts = Mockito.mock(FontLoader.class);
        Mockito.when(fonts.getFont(FontLoader.TEXT_FONT)).thenReturn(font);
        fontLoader = scoped(Mockito.mockStatic(FontLoader.class));
        fontLoader.when(FontLoader::getInstance).thenReturn(fonts);

        updates = new UpdateTaskManager();
        network = Mockito.mock(NetComm.class);
        GameGui gui = Mockito.mock(GameGui.class);
        Mockito.when(gui.getDialogGui()).thenReturn(handler);

        world = scoped(Mockito.mockStatic(World.class));
        world.when(World::getUpdateTaskManager).thenReturn(updates);
        world.when(World::getNet).thenReturn(network);
        world.when(World::getGameGui).thenReturn(gui);
    }

    private void start() {
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
        Mockito.verifyNoMoreInteractions(network);
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
        Mockito.verifyNoMoreInteractions(network);
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
        Mockito.verifyNoMoreInteractions(network);
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
        CraftingItemEntry selection = Mockito.mock(CraftingItemEntry.class);
        Mockito.when(selection.getItemIndex()).thenReturn(7);

        Mockito.when(crafting.getSelectedCraftingItem()).thenReturn(selection);
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
        start();
        handler.handleCraftingCloseDialogEvent("craftingDialog", new DialogCraftingCloseEvent(0));
        handler.handleCraftingCloseDialogEvent("craftingDialog", new DialogCraftingCloseEvent(0));
        Mockito.verify(network).sendCommand(Mockito.isA(CloseDialogCraftingCmd.class));
        frame();
        assertFalse(element.isVisible());
        open(0);
        frame();
        assertTrue(element.isVisible());
        Mockito.verifyNoMoreInteractions(network);
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
        Mockito.verifyNoMoreInteractions(network);
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

    @DataProvider(name = "dialogTypes")
    public Object[][] dialogTypes() {
        return new Object[][] {
            {"Crafting"}, {"Merchant"}, {"Message"},
            {"Input"}, {"Selection"}
        };
    }

    private void openDialog(DialogType type) {
        switch (type) {
            case Crafting:
                open(0);
                break;
            case Merchant:
                handler.showMerchantDialog(0, "Merchant", Collections.emptyList());
                break;
            case Message:
                handler.showMessageDialog(0, "Message", "Text");
                break;
            case Input:
                handler.showInputDialog(0, "Input", "Text", 20, false);
                break;
            case Selection:
                handler.showSelectionDialog(0, "Selection", "Text", Collections.emptyList());
                break;
            default:
                throw new AssertionError(type);
        }
    }

    private boolean isDialogVisible(DialogType type) {
        switch (type) {
            case Crafting:
                return element.isVisible();
            case Merchant:
                return merchantElement.isVisible();
            case Message:
                return isWindowVisible("msgDialog0");
            case Input:
                return isWindowVisible("inputDialog0");
            case Selection:
                return isWindowVisible("selectDialog0");
            default:
                throw new AssertionError(type);
        }
    }

    private boolean isWindowVisible(String id) {
        return screen.findElementById("windows").getChildren().stream()
                .anyMatch(child -> id.equals(child.getId()) && child.isVisible());
    }

    @Test(dataProvider = "dialogTypes")
    public void testEarlierCloseDoesNotOvertakeOpen(String typeName) {
        DialogType type = DialogType.valueOf(typeName);
        setUp(false);

        handler.closeDialog(0, EnumSet.of(type));
        openDialog(type);
        frame();
        assertTrue(isDialogVisible(type));
        Mockito.verifyNoMoreInteractions(network);
    }

    @Test(dataProvider = "dialogTypes")
    public void testOpenThenCloseInOneFrameEndsClosed(String typeName) {
        DialogType type = DialogType.valueOf(typeName);
        setUp(false);

        openDialog(type);
        handler.closeDialog(0, EnumSet.of(type));
        frame();
        assertFalse(isDialogVisible(type));
        Mockito.verifyNoMoreInteractions(network);
    }

    @Test(dataProvider = "dialogTypes")
    public void testCloseThenReopenInOneFrameEndsOpen(String typeName) {
        DialogType type = DialogType.valueOf(typeName);
        setUp(false);

        openDialog(type);
        frame();
        assertTrue(isDialogVisible(type));
        handler.closeDialog(0, EnumSet.of(type));
        openDialog(type);
        frame();
        nifty.update();
        assertTrue(isDialogVisible(type));
        Mockito.verifyNoMoreInteractions(network);
    }

    @Test
    public void testCloseAllDoesNotCloseLaterCraftingRequest() {
        setUp(false);
        start();
        handler.closeDialog(DialogGui.ALL_DIALOGS, EnumSet.allOf(DialogType.class));
        open(0);
        frame();
        assertTrue(element.isVisible());
    }

    @Test
    public void testClosePreventsLaterProductionUpdate() {
        setUp(false);
        start();
        close(0);
        handler.startProductionIndicator(0, 8, 20);
        frame();
        assertEquals(amount, 1);
        assertFalse(handler.isCraftingInProgress());
    }

    @Test
    public void testScreenEndDiscardsPendingDialogRequests() {
        setUp(false);
        start();
        open(0);
        handler.onEndScreen();
        frame();
        assertFalse(element.isVisible());
    }

    @Test
    public void testQueuedOpenStartAndAbortRunInOrder() {
        setUp(false);

        open(0);
        handler.startProductionIndicator(0, 8, 20);
        handler.abortProduction(0);
        frame();
        assertTrue(element.isVisible());
        assertEquals(amount, 8);
        assertEquals(progress, 0.f);
        assertFalse(handler.isCraftingInProgress());
    }

    @Test
    public void testCompletionAfterCloseIsIgnored() {
        setUp(false);
        start();
        close(0);
        handler.finishProduction(0);
        frame();
        assertFalse(element.isVisible());
        assertEquals(amount, 1);
        Mockito.verifyNoMoreInteractions(network);
    }

    @Test
    public void testCloseRetainsRequestedTypes() {
        setUp(false);
        start();
        EnumSet<DialogType> types = EnumSet.of(DialogType.Crafting);
        handler.closeDialog(0, types);
        types.clear();
        types.add(DialogType.Message);
        frame();
        assertFalse(element.isVisible());
    }
}
