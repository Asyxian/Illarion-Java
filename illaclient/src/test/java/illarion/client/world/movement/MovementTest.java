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
package illarion.client.world.movement;

import illarion.client.graphics.MapDisplayManager;
import illarion.client.graphics.MoveAnimation;
import illarion.client.net.NetComm;
import illarion.client.net.client.AbstractCommand;
import illarion.client.net.client.MoveCmd;
import illarion.client.test.ScopedMocks;
import illarion.client.test.TestObjects;
import illarion.client.util.UpdateTaskManager;
import illarion.client.world.Char;
import illarion.client.world.CharMovementMode;
import illarion.client.world.Player;
import illarion.client.world.World;
import illarion.common.types.CharacterId;
import illarion.common.types.Direction;
import illarion.common.types.ServerCoordinate;
import org.illarion.engine.GameContainer;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class MovementTest extends ScopedMocks {
    private static final ServerCoordinate ORIGIN = new ServerCoordinate(359, 876, 0);
    private static final ServerCoordinate FIRST_STEP = new ServerCoordinate(359, 875, 0);
    private static final ServerCoordinate WARP = new ServerCoordinate(359, 878, 1);
    private static final ServerCoordinate SECOND_STEP = new ServerCoordinate(358, 877, 1);

    private MockedStatic<World> world;
    private Movement movement;
    private MoveAnimator animator;
    private UpdateTaskManager updates;
    private Queue<Runnable> pendingTasks;
    private GameContainer container;
    private ServerCoordinate location;
    private Direction direction;
    private int commandsSent;

    @BeforeMethod
    public void setUp() {
        location = ORIGIN;
        direction = Direction.North;
        commandsSent = 0;
        updates = new UpdateTaskManager();
        pendingTasks = new ConcurrentLinkedQueue<>();
        container = Mockito.mock(GameContainer.class);
        Player player = Mockito.mock(Player.class);
        Char character = Mockito.mock(Char.class);
        MapDisplayManager display = Mockito.mock(MapDisplayManager.class);
        NetComm network = Mockito.mock(NetComm.class);
        MoveAnimation animation = Mockito.mock(MoveAnimation.class);
        ExecutorService executor = Mockito.mock(ExecutorService.class);
        Mockito.when(executor.submit(Mockito.any(Runnable.class))).thenAnswer(invocation -> {
            pendingTasks.add((Runnable) invocation.getArguments()[0]);
            return CompletableFuture.completedFuture(null);
        });
        Mockito.when(player.getCharacter()).thenReturn(character);
        Mockito.when(player.getLocation()).thenAnswer(invocation -> location);
        Mockito.when(character.getLocation()).thenAnswer(invocation -> location);
        Mockito.doAnswer(this::updateLocation).when(player).setLocation(Mockito.any(ServerCoordinate.class));
        Mockito.doAnswer(this::updateLocation).when(player).updateLocation(Mockito.any(ServerCoordinate.class));
        Mockito.doAnswer(invocation -> {
            direction = (Direction) invocation.getArguments()[0];
            return null;
        }).when(character).setDirection(Mockito.any(Direction.class));
        Mockito.doAnswer(invocation -> {
            commandsSent++;
            return null;
        }).when(network).sendCommand(Mockito.any(AbstractCommand.class));

        movement = TestObjects.newInstance(Movement.class);
        TestObjects.setInternalState(movement, "player", player);
        TestObjects.setInternalState(movement, "playerLocation", ORIGIN);
        TestObjects.setInternalState(movement, "executorService", executor);
        TestObjects.setInternalState(movement, "moveAnimation", animation);
        animator = new MoveAnimator(movement, animation);
        TestObjects.setInternalState(movement, "animator", animator);
        world = scoped(Mockito.mockStatic(World.class));
        world.when(World::getPlayer).thenReturn(player);
        world.when(World::getUpdateTaskManager).thenReturn(updates);
        world.when(World::getMapDisplay).thenReturn(display);
        world.when(World::getNet).thenReturn(network);
    }

    private Object updateLocation(InvocationOnMock invocation) {
        location = (ServerCoordinate) invocation.getArguments()[0];
        return null;
    }

    @Test
    public void queuedConfirmationBeforeWarpCannotRestoreTheOldLocation() {
        confirm(FIRST_STEP);
        movement.executeServerLocation(WARP);
        drainTasks();
        updates.onUpdateGame(container, 0);
        assertEquals(location, WARP);
        assertEquals(movement.getServerLocation(), WARP);
    }

    @Test
    public void queuedTurnBeforeWarpIsDiscarded() {
        movement.executeServerRespTurn(Direction.South);
        movement.executeServerLocation(WARP);
        drainTasks();
        updates.onUpdateGame(container, 0);
        assertEquals(direction, Direction.North);
    }

    @Test
    public void deferredCancellationBeforeWarpCannotResetTheLocation() {
        animator.cancelMove(ORIGIN);
        movement.executeServerLocation(WARP);
        updates.onUpdateGame(container, 0);
        assertEquals(location, WARP);
    }

    @Test
    public void postWarpConfirmationIsStillExecuted() {
        confirm(FIRST_STEP);
        movement.executeServerLocation(WARP);
        confirm(SECOND_STEP);
        drainTasks();
        updates.onUpdateGame(container, 0);
        drainTasks();
        assertEquals(location, SECOND_STEP);
        assertEquals(movement.getServerLocation(), SECOND_STEP);
        assertFalse(TestObjects.<Boolean>getInternalState(movement, "stepInProgress"));
    }

    @Test
    public void warpUpdatesLogicalLocationBeforeReturning() {
        movement.executeServerLocation(WARP);
        assertEquals(location, WARP);
        assertEquals(movement.getServerLocation(), WARP);
    }

    @Test
    public void repeatedWarpsDiscardOnlyEarlierWork() {
        movement.executeServerLocation(WARP);
        confirm(SECOND_STEP);
        movement.executeServerLocation(ORIGIN);
        confirm(FIRST_STEP);
        drainTasks();
        updates.onUpdateGame(container, 0);
        assertEquals(location, FIRST_STEP);
        assertEquals(movement.getServerLocation(), FIRST_STEP);
    }

    @Test
    public void queuedRetryDoesNotResendThePreWarpCommand() {
        setPreviousCommand();
        movement.executeServerRespMoveTooEarly();
        movement.executeServerLocation(WARP);
        drainTasks();
        assertEquals(commandsSent, 0);
    }

    @Test
    public void retryAfterWarpHasNoPreWarpCommandToResend() {
        setPreviousCommand();
        movement.executeServerLocation(WARP);
        movement.executeServerRespMoveTooEarly();
        drainTasks();
        assertEquals(commandsSent, 0);
    }

    @Test
    public void warpDoesNotDisengageANewlyActivatedHandler() {
        MovementHandler previous = createHandler();
        MovementHandler next = createHandler();
        movement.activate(previous);
        movement.executeServerLocation(WARP);
        movement.activate(next);
        updates.onUpdateGame(container, 0);
        drainTasks();
        assertTrue(movement.isActive(next));
    }

    @Test
    public void shutdownInvalidatesPendingMovementTasks() {
        confirm(FIRST_STEP);
        movement.shutdown();
        drainTasks();
        updates.onUpdateGame(container, 0);
        assertEquals(location, ORIGIN);
    }

    @Test
    public void warpDoesNotWaitForPathFindingOrApplyItsObsoleteResult() throws Exception {
        CountDownLatch calculating = new CountDownLatch(1);
        CountDownLatch finishCalculation = new CountDownLatch(1);
        AtomicInteger actions = new AtomicInteger();
        MovementHandler handler = new AbstractMovementHandler(movement) {
            @Override
            public StepData getNextStep(ServerCoordinate currentLocation) {
                assertEquals(currentLocation, ORIGIN);
                calculating.countDown();

                try {
                    assertTrue(finishCalculation.await(5, TimeUnit.SECONDS), "The calculation must be released");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }

                return new DefaultStepData(CharMovementMode.None, null, actions::incrementAndGet);
            }
        };
        movement.activate(handler);
        FutureTask<Void> calculation = new FutureTask<>(pendingTasks.remove(), null);
        Thread calculationThread = new Thread(calculation, "test-path-calculation");
        calculationThread.setDaemon(true);
        calculationThread.start();
        FutureTask<Void> warp = new FutureTask<>(() -> movement.executeServerLocation(WARP), null);
        Thread warpThread = new Thread(warp, "test-warp");
        warpThread.setDaemon(true);

        try {
            assertTrue(calculating.await(5, TimeUnit.SECONDS));
            warpThread.start();
            warp.get(2, TimeUnit.SECONDS);
        } finally {
            finishCalculation.countDown();
            calculation.get(5, TimeUnit.SECONDS);
            warp.get(5, TimeUnit.SECONDS);
        }

        drainTasks();
        assertEquals(actions.get(), 0, "Do not apply a path result calculated before the warp");
        assertEquals(location, WARP);
    }

    @Test
    public void predictionCorrectionDoesNotDiscardLaterServerConfirmations() {
        MovingTask prediction = new MovingTask(animator, CharMovementMode.Walk, ORIGIN, 400);
        TestObjects.setInternalState(prediction, "executed", true);
        TestObjects.setInternalState(animator, "uncomfirmedMoveTask", prediction);
        MoveAnimation animation = TestObjects.getInternalState(movement, "moveAnimation");
        Mockito.when(animation.isRunning()).thenReturn(true);
        confirm(FIRST_STEP);
        confirm(SECOND_STEP);
        drainTasks();
        updates.onUpdateGame(container, 0);
        assertEquals(location, SECOND_STEP);
        assertEquals(movement.getServerLocation(), SECOND_STEP);
    }

    private MovementHandler createHandler() {
        return new AbstractMovementHandler(movement) {
            @Override
            public StepData getNextStep(ServerCoordinate currentLocation) {
                return null;
            }
        };
    }

    private void setPreviousCommand() {
        MoveCmd command = new MoveCmd(new CharacterId(1), CharMovementMode.Walk, Direction.North);
        TestObjects.setInternalState(movement, "lastSendMoveCommand", command);
    }

    private void confirm(ServerCoordinate target) {
        movement.executeServerRespMove(CharMovementMode.None, target, 0);
    }

    private void drainTasks() {
        while (!pendingTasks.isEmpty()) {
            pendingTasks.remove().run();
        }
    }
}
