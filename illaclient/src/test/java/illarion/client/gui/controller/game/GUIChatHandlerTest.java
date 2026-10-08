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
import de.lessvoid.nifty.builder.ControlBuilder;
import de.lessvoid.nifty.builder.LayerBuilder;
import de.lessvoid.nifty.builder.PanelBuilder;
import de.lessvoid.nifty.builder.ScreenBuilder;
import de.lessvoid.nifty.controls.Label;
import de.lessvoid.nifty.controls.ScrollPanel;
import de.lessvoid.nifty.controls.Scrollbar;
import de.lessvoid.nifty.elements.Element;
import de.lessvoid.nifty.screen.Screen;
import de.lessvoid.nifty.screen.ScreenController;
import de.lessvoid.nifty.spi.input.InputSystem;
import de.lessvoid.nifty.spi.render.RenderDevice;
import de.lessvoid.nifty.spi.render.RenderFont;
import de.lessvoid.nifty.spi.render.RenderImage;
import de.lessvoid.nifty.spi.sound.SoundDevice;
import de.lessvoid.nifty.tools.Color;
import de.lessvoid.nifty.tools.SizeValue;
import illarion.client.Game;
import illarion.client.IllaClient;
import illarion.client.gui.GameGui;
import illarion.client.test.ScopedMocks;
import illarion.client.test.TestObjects;
import illarion.client.world.World;
import illarion.common.config.Config;
import org.illarion.engine.GameContainer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GUIChatHandlerTest extends ScopedMocks {
    private MockedStatic<World> world;
    private MockedStatic<IllaClient> client;
    private Game game;
    private GameContainer container;
    private boolean worldInitialised;

    private int width;
    private int height;
    private Nifty nifty;
    private GUIChatHandler handler;
    private ScrollPanel scroll;
    private Element content;

    @BeforeMethod
    public void setUp() throws Exception {
        width = 1000;
        height = 700;
        RenderFont font = Mockito.mock(RenderFont.class);
        Mockito.when(font.getHeight()).thenReturn(16);
        Mockito.when(font.getWidth(Mockito.anyString())).thenAnswer(this::textWidth);
        Mockito.when(font.getWidth(Mockito.anyString(), Mockito.anyFloat()))
                .thenAnswer(this::textWidth);
        Mockito.when(font.getCharacterAdvance(Mockito.anyChar(), Mockito.anyChar(), Mockito.anyFloat()))
                .thenReturn(8);
        RenderImage image = Mockito.mock(RenderImage.class);
        Mockito.when(image.getWidth()).thenReturn(64);
        Mockito.when(image.getHeight()).thenReturn(64);
        RenderDevice render = Mockito.mock(RenderDevice.class);
        Mockito.when(render.getWidth()).thenAnswer(invocation -> width);
        Mockito.when(render.getHeight()).thenAnswer(invocation -> height);
        Mockito.when(render.createFont(Mockito.anyString())).thenReturn(font);
        Mockito.when(render.createImage(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(image);
        SoundDevice sound = Mockito.mock(SoundDevice.class);
        InputSystem input = Mockito.mock(InputSystem.class);
        ScreenController controller = Mockito.mock(ScreenController.class);

        nifty = new Nifty(render, sound, input, () -> 1000L);
        nifty.loadStyleFile("nifty-illarion-style.xml");
        nifty.loadControlFile("nifty-default-controls.xml");
        ScreenBuilder builder = new ScreenBuilder("game", controller);
        LayerBuilder layer = new LayerBuilder("layer");
        layer.childLayoutCenter();
        PanelBuilder outer = new PanelBuilder("outer");
        outer.childLayoutVertical();
        PanelBuilder inner = new PanelBuilder("inner");
        inner.childLayoutVertical();
        ControlBuilder panel = new ControlBuilder("chatPanel", "scrollPanel");
        panel.width("600px");
        panel.height("180px");
        panel.style("nifty-chatlog");
        panel.parameter("vertical", "true");
        panel.parameter("horizontal", "false");
        panel.parameter("autoScroll", "off");
        panel.parameter("stepSizeY", "20");
        PanelBuilder log = new PanelBuilder("chatLog");
        log.childLayoutVertical();
        log.width("574px");
        panel.panel(log);
        inner.control(panel);
        outer.panel(inner);
        layer.panel(outer);
        builder.layer(layer);
        Screen screen = builder.build(nifty);
        nifty.addScreen("game", screen);
        nifty.gotoScreen("game");
        scroll = screen.findNiftyControl("chatPanel", ScrollPanel.class);
        content = screen.findElementById("chatLog");
        // Exercise real chat/layout code without constructing the unrelated translation service.
        handler = TestObjects.newInstance(GUIChatHandler.class);
        TestObjects.setInternalState(handler, "nifty", nifty);
        TestObjects.setInternalState(handler, "screen", screen);
        TestObjects.setInternalState(handler, "chatLog", scroll);
        TestObjects.setInternalState(handler, "chatLineCounter", new AtomicLong());
        game = TestObjects.newInstance(Game.class);
        TestObjects.setInternalState(game, "nifty", nifty);
        TestObjects.setInternalState(game, "activeListener", -1);
        container = Mockito.mock(GameContainer.class);
        GameGui gui = Mockito.mock(GameGui.class);
        Mockito.when(gui.getChatGui()).thenReturn(handler);
        Config config = Mockito.mock(Config.class);

        worldInitialised = true;
        world = scoped(Mockito.mockStatic(World.class));
        world.when(World::isInitDone).thenAnswer(invocation -> worldInitialised);
        world.when(World::getGameGui).thenReturn(gui);
        client = scoped(Mockito.mockStatic(IllaClient.class));
        client.when(IllaClient::getCfg).thenReturn(config);

        appendMessages(60);
        finishLayout();
        assertTrue(scroll.getVerticalPos() > 200, "The fixture must have a scrollable history");
        scroll.setVerticalPos(200);
    }

    private int textWidth(InvocationOnMock invocation) {
        return ((String) invocation.getArguments()[0]).length() * 8;
    }

    @Test
    public void noNewMessageKeepsReadingPosition() throws Exception {
        finishLayout();
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void newMessageKeepsReadingPosition() throws Exception {
        append("Another message");
        finishLayout();
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void newMessagesFollowTheBottom() throws Exception {
        scrollToBottom();
        float previous = scroll.getVerticalPos();
        appendMessages(3);
        finishLayout();
        assertTrue(scroll.getVerticalPos() > previous);
        assertAtBottom();
    }

    @Test
    public void scrollingBackToTheBottomResumesFollowing() throws Exception {
        append("Do not follow yet");
        finishLayout();
        assertEquals(scroll.getVerticalPos(), 200f);
        scrollToBottom();
        append("Follow this message");
        finishLayout();
        assertAtBottom();
    }

    @Test
    public void batchOfNewMessagesKeepsReadingPosition() throws Exception {
        appendMessages(20);
        finishLayout();
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void pruningOldEntriesKeepsTheSameMessageVisible() throws Exception {
        Element anchor = content.getChildren().get(60);
        scroll.setVerticalPos(anchor.getY() - content.getY() + 5);
        float previous = relativePosition(anchor);
        appendMessages(150);
        finishLayout();
        assertEquals(content.getChildrenCount(), 400);
        assertEquals(relativePosition(anchor), previous);
    }

    @Test
    public void expiredReadingAnchorFallsBackToOldestRemainingMessage() throws Exception {
        scroll.setVerticalPos(0);
        appendMessages(150);
        finishLayout();
        assertEquals(content.getChildrenCount(), 400);
        assertEquals(scroll.getVerticalPos(), 0f);
    }

    @Test
    public void pruningWhileAtTheBottomKeepsFollowing() throws Exception {
        scrollToBottom();
        appendMessages(150);
        finishLayout();
        assertEquals(content.getChildrenCount(), 400);
        assertAtBottom();
    }

    @Test
    public void translationLayoutAboveTheReaderKeepsTheSameMessageVisible() throws Exception {
        Element anchor = content.getChildren().get(60);
        scroll.setVerticalPos(anchor.getY() - content.getY() + 5);
        float previous = relativePosition(anchor);
        TestObjects.invokeMethod(handler, "rememberChatPosition");
        Element translation = content.getChildren().get(1);
        translation.setMarginTop(SizeValue.def());
        translation.setVisible(true);
        translation.getNiftyControl(Label.class).setText("A translation inserted above the current reading position.");
        TestObjects.setInternalState(handler, "dirty", true);
        finishLayout();
        assertEquals(relativePosition(anchor), previous);
    }

    @Test
    public void windowResizeKeepsReadingPosition() {
        resize(1200, 800);
        assertEquals(scroll.getVerticalPos(), 200f);
        resize(800, 600);
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void windowResizeAtBottomKeepsFollowing() {
        scrollToBottom();
        resize(1200, 800);
        assertAtBottom();
    }

    @Test
    public void multipleResizeNotificationsBeforeRenderingKeepReadingPosition() {
        game.resize(container, 1100, 750);
        game.resize(container, 1200, 800);
        width = 1200;
        height = 800;
        game.render(container);
        assertEquals(scroll.getVerticalPos(), 200f);
        game.render(container);
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void rewrappingKeepsTheSameMessageVisible() {
        Element anchor = content.getChildren().get(60);
        scroll.setVerticalPos(anchor.getY() - content.getY() + 5);
        float previous = relativePosition(anchor);

        for (Element entry : content.getChildren()) {
            entry.setConstraintWidth(SizeValue.px(300));
        }

        resize(800, 600);
        assertEquals(relativePosition(anchor), previous);
    }

    @Test
    public void expandingAndCollapsingTheChatKeepsReadingPosition() throws Exception {
        TestObjects.invokeMethod(handler, "setHeightOfChatLog", new Class<?>[]{SizeValue.class}, SizeValue.px(500));
        assertEquals(scroll.getVerticalPos(), 200f);
        TestObjects.invokeMethod(handler, "setHeightOfChatLog", new Class<?>[]{SizeValue.class}, SizeValue.px(170));
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void expandingTheChatAtBottomKeepsFollowing() throws Exception {
        scrollToBottom();
        TestObjects.invokeMethod(handler, "setHeightOfChatLog", new Class<?>[]{SizeValue.class}, SizeValue.px(500));
        assertAtBottom();
    }

    @Test
    public void resizeWithNewMessagesKeepsReadingPosition() throws Exception {
        game.resize(container, 1200, 800);
        appendMessages(3);
        TestObjects.invokeMethod(handler, "cleanupChatLog");
        width = 1200;
        height = 800;
        game.render(container);
        assertEquals(scroll.getVerticalPos(), 200f);
    }

    @Test
    public void resizingBeforeWorldInitialisationDoesNotAccessChat() {
        worldInitialised = false;
        world.reset();
        world.when(World::isInitDone).thenReturn(false);

        resize(1200, 800);
        world.verify(World::getGameGui, Mockito.never());
    }

    private void resize(int newWidth, int newHeight) {
        width = newWidth;
        height = newHeight;
        game.resize(container, width, height);
        game.render(container);
    }

    private float relativePosition(Element entry) {
        return entry.getY() - content.getY() - scroll.getVerticalPos();
    }

    private void appendMessages(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            append("Message " + i + ": this is a chat entry long enough to have a real text layout.");
        }
    }

    private void append(String text) throws Exception {
        TestObjects.invokeMethod(handler, "addChatLogText", new Class<?>[]{String.class, Color.class},
                text, Color.WHITE);
    }

    private void finishLayout() throws Exception {
        TestObjects.invokeMethod(handler, "cleanupChatLog");
        nifty.update();
        nifty.render(false);
    }

    private void scrollToBottom() {
        scroll.setAutoScroll(ScrollPanel.AutoScroll.BOTTOM);
        scroll.setAutoScroll(ScrollPanel.AutoScroll.OFF);
    }

    private void assertAtBottom() {
        Scrollbar bar = scroll.getElement().findNiftyControl("#nifty-internal-vertical-scrollbar", Scrollbar.class);
        assertEquals(scroll.getVerticalPos(), bar.getWorldMax() - bar.getWorldPageSize());
    }
}
