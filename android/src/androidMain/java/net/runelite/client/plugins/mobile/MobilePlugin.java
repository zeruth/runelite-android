package net.runelite.client.plugins.mobile;

import net.runelite.mp.ui.bridge.PinnedKeys;
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
import net.runelite.api.events.BeforeRender;
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
	@Inject private Injector injector;
	@Inject private ConfigManager configManager;
	@Inject private MobileConfig config;
	@Inject private OverlayManager overlayManager;
	@Inject private MobileInventoryOverlay inventoryOverlay;

	@Override
	protected void startUp()
	{
		MobileSettingsEntry.register(injector, configManager, config);
		overlayManager.add(inventoryOverlay);
		MobileDisplaySettings.setPortrait(config.forceVertical());
		PinnedKeys.INSTANCE.update(config);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(inventoryOverlay);
		MobileInventoryOverlay.clear();
		MobileDisplaySettings.setPortrait(false);
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

	@Subscribe
	public void onBeforeRender(BeforeRender event) { inventoryOverlay.validateVisibility(); }

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) { MobileInventoryOverlay.clear(); }

	@Provides
	MobileConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MobileConfig.class);
	}
}
