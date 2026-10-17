/*
 * Copyright (c) 2026
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.ui.overlay;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.FontType;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientUI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OverlayRendererTest
{
	@Mock private Client client;
	@Mock private OverlayManager overlayManager;
	@Mock private RuneLiteConfig config;
	@Mock private MouseManager mouseManager;
	@Mock private KeyManager keyManager;
	@Mock private ClientUI clientUI;
	@Mock private EventBus eventBus;
	@Mock private ChatMessageManager chatMessageManager;
	@Mock private SnapCorners snapCorners;
	@InjectMocks private OverlayRenderer renderer;

	@Before
	public void setUp()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getCanvasWidth()).thenReturn(100);
		when(client.getCanvasHeight()).thenReturn(100);
		when(client.getRealDimensions()).thenReturn(new Dimension(100, 100));
		when(config.dynamicOverlayFont()).thenReturn(FontType.SMALL);
		when(config.tooltipFont()).thenReturn(FontType.SMALL);
		when(config.interfaceFont()).thenReturn(FontType.REGULAR);
	}

	@Test
	public void linkageFailureDoesNotStopOtherOverlaysOrRepeatNextFrame()
	{
		for (LinkageError failure : Arrays.asList(new NoSuchMethodError("fill3DRect"),
			new NoClassDefFoundError("math library"), new AbstractMethodError("render")))
		{
			TestOverlay broken = new TestOverlay(g ->
			{
				g.translate(40, 40);
				g.setColor(Color.RED);
				g.setClip(null);
				throw failure;
			});
			broken.getBounds().setSize(20, 20);
			TestOverlay healthy = new TestOverlay(g ->
			{
				assertEquals(new AffineTransform(), g.getTransform());
				assertEquals(new Rectangle(0, 0, 100, 100), g.getClipBounds());
				assertEquals(Color.BLUE, g.getColor());
				g.fillRect(5, 5, 1, 1);
			});
			BufferedImage image = renderTwoFrames(broken, healthy);
			assertEquals(1, broken.renders);
			assertEquals(2, healthy.renders);
			assertEquals(Color.BLUE.getRGB(), image.getRGB(5, 5));
			assertTrue(broken.getBounds().isEmpty());
		}
	}

	@Test
	public void ordinaryExceptionsStillAllowRetries()
	{
		TestOverlay broken = new TestOverlay(g ->
		{
			throw new IllegalStateException("temporary");
		});
		TestOverlay healthy = new TestOverlay(g ->
		{
		});
		renderTwoFrames(broken, healthy);
		assertEquals(2, broken.renders);
		assertEquals(2, healthy.renders);
	}

	@Test
	public void virtualMachineErrorsAreNotSwallowed()
	{
		OutOfMemoryError failure = new OutOfMemoryError("simulated");
		TestOverlay broken = new TestOverlay(g ->
		{
			throw failure;
		});
		TestOverlay healthy = new TestOverlay(g ->
		{
		});
		try
		{
			renderTwoFrames(broken, healthy);
			fail("VirtualMachineError must escape the overlay guard");
		}
		catch (OutOfMemoryError actual)
		{
			assertSame(failure, actual);
		}
		assertEquals(0, healthy.renders);
	}

	private BufferedImage renderTwoFrames(TestOverlay... overlays)
	{
		when(overlayManager.getForInterface(621)).thenReturn(Arrays.asList(overlays));
		BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try
		{
			graphics.setColor(Color.BLUE);
			renderer.renderAfterInterface(graphics, 621, Collections.emptyList());
			renderer.renderAfterInterface(graphics, 621, Collections.emptyList());
		}
		finally
		{
			graphics.dispose();
		}
		return image;
	}

	private static class TestOverlay extends Overlay
	{
		private final Consumer<Graphics2D> draw;
		private int renders;

		private TestOverlay(Consumer<Graphics2D> draw)
		{
			this.draw = draw;
			setPosition(OverlayPosition.DYNAMIC);
		}

		@Override
		public Dimension render(Graphics2D graphics)
		{
			renders++;
			draw.accept(graphics);
			return new Dimension(10, 10);
		}
	}
}
