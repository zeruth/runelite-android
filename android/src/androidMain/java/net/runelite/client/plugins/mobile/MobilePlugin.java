package net.runelite.client.plugins.mobile;

import net.runelite.mp.ui.bridge.PinnedKeys;
import net.runelite.mp.ui.bridge.ModifierState;
import com.google.inject.Injector;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.config.MobileSettingsEntry;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.FocusChanged;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.GameStateChanged;

/**
 * Home for mobile-only client settings. Hidden so it has no on/off toggle; instead it adds
 * a pinned, config-only "Mobile" row to the plugin list (see {@link MobileSettingsEntry}).
 * The sidebar host reads {@link MobileConfig#SIDEBAR_SCALE} straight from the
 * ConfigManager each repaint, so the setting applies live.
 */
@PluginDescriptor(
	name = "Mobile",
	description = "Mobile client settings",
	hidden = true
)
public class MobilePlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private Injector injector;
	@Inject private ConfigManager configManager;
	@Inject private MobileConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private MobileInventoryOverlay inventoryOverlay;

	private final MobileChatLayout chatLayout = new MobileChatLayout();
	private volatile boolean active;

	@Override
	protected void startUp()
	{
		active = true;
		MobileSettingsEntry.register(injector, configManager, config);
		overlayManager.add(inventoryOverlay);
		MobileDisplaySettings.setPortrait(config.forceVertical());
		PinnedKeys.INSTANCE.update(config);
	}

	@Override
	protected void shutDown()
	{
		active = false;
		ModifierState.INSTANCE.reset();
		clientThread.invokeLater(() ->
		{
			if (!active)
			{
				chatLayout.restore();
			}
		});
		overlayManager.remove(inventoryOverlay);
		MobileInventoryOverlay.clear();
		MobileDisplaySettings.setPortrait(false);
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		ModifierState.INSTANCE.afterClientTick(client);
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		ModifierState.INSTANCE.onCanvasFocusChanged(event.isFocused());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MobileConfig.GROUP.equals(event.getGroup()))
		{
			MobileDisplaySettings.setPortrait(config.forceVertical());
			PinnedKeys.INSTANCE.update(config);
		}
	}

	// Update native widget positions before MobileInputPlugin publishes touch bounds.
	@Subscribe(priority = 1)
	public void onBeforeRender(BeforeRender event)
	{
		updateChatLayout();
		inventoryOverlay.validateVisibility();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BUILD_CHATBOX
			|| event.getScriptId() == ScriptID.SPLITPM_CHANGED
			|| event.getScriptId() == ScriptID.TOPLEVEL_REDRAW)
		{
			updateChatLayout();
		}
	}

	private void updateChatLayout()
	{
		if (active)
		{
			chatLayout.update(client, config.invertChat(), config.chatScale());
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		MobileInventoryOverlay.clear();
		updateChatLayout();
	}

	@Provides
	MobileConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MobileConfig.class);
	}
}
