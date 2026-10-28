package net.runelite.client.plugins.mobile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MobileConfig.GROUP)
public interface MobileConfig extends Config
{
	String GROUP = "mobile";
	String SIDEBAR_SCALE = "sidebarScale";

	@ConfigItem(keyName = "forceVertical", name = "Force vertical",
		description = "Keep the app in portrait orientation. Disable to return to landscape.", position = 2)
	default boolean forceVertical() { return false; }

	@ConfigItem(keyName = "inventoryScale", name = "Inventory scale", description = "Saved pinch size", hidden = true)
	default int inventoryScale() { return 100; }

	@ConfigItem(keyName = "chatScale", name = "Chat scale", description = "Saved pinch size", hidden = true)
	default int chatScale() { return 100; }

	@ConfigItem(keyName = "minimapScale", name = "Minimap scale", description = "Saved pinch size", hidden = true)
	default int minimapScale() { return 100; }

	@ConfigItem(keyName = "staticTabsScale", name = "Static tabs scale", description = "Saved pinch size", hidden = true)
	default int staticTabsScale() { return 100; }

	@ConfigItem(keyName = "movableTabsScale", name = "Movable tabs scale", description = "Saved pinch size", hidden = true)
	default int movableTabsScale() { return 100; }


	@ConfigSection(name = "Pinned keys", description = "Keys shown on the mobile toolbar", position = 3)
	String pinnedKeys = "pinnedKeys";

	@ConfigItem(keyName = "pinKeysAtBottom", name = "Pin keys at bottom",
		description = "Place pinned keys below the plugin icons instead of above them.", section = pinnedKeys, position = 0)
	default boolean pinKeysAtBottom() { return false; }

	@ConfigItem(keyName = "pinControl", name = "Pin Control",
		description = "Show Control on the toolbar.", section = pinnedKeys, position = 1)
	default boolean pinControl() { return false; }

	@ConfigItem(keyName = "pinShift", name = "Pin Shift",
		description = "Show Shift on the toolbar.", section = pinnedKeys, position = 2)
	default boolean pinShift() { return true; }

	@ConfigItem(keyName = "pinAlt", name = "Pin Alt",
		description = "Show Alt on the toolbar.", section = pinnedKeys, position = 3)
	default boolean pinAlt() { return true; }

	@ConfigItem(keyName = "pinSpace", name = "Pin Space",
		description = "Show Space on the toolbar.", section = pinnedKeys, position = 4)
	default boolean pinSpace() { return false; }

	@ConfigItem(keyName = "pin0", name = "Pin 0",
		description = "Show 0 on the toolbar.", section = pinnedKeys, position = 5)
	default boolean pin0() { return false; }

	@ConfigItem(keyName = "pin1", name = "Pin 1",
		description = "Show 1 on the toolbar.", section = pinnedKeys, position = 6)
	default boolean pin1() { return false; }

	@ConfigItem(keyName = "pin2", name = "Pin 2",
		description = "Show 2 on the toolbar.", section = pinnedKeys, position = 7)
	default boolean pin2() { return false; }

	@ConfigItem(keyName = "pin3", name = "Pin 3",
		description = "Show 3 on the toolbar.", section = pinnedKeys, position = 8)
	default boolean pin3() { return false; }

	@ConfigItem(keyName = "pin4", name = "Pin 4",
		description = "Show 4 on the toolbar.", section = pinnedKeys, position = 9)
	default boolean pin4() { return false; }

	@ConfigItem(keyName = "pin5", name = "Pin 5",
		description = "Show 5 on the toolbar.", section = pinnedKeys, position = 10)
	default boolean pin5() { return false; }

	@ConfigItem(keyName = "pin6", name = "Pin 6",
		description = "Show 6 on the toolbar.", section = pinnedKeys, position = 11)
	default boolean pin6() { return false; }

	@ConfigItem(keyName = "pin7", name = "Pin 7",
		description = "Show 7 on the toolbar.", section = pinnedKeys, position = 12)
	default boolean pin7() { return false; }

	@ConfigItem(keyName = "pin8", name = "Pin 8",
		description = "Show 8 on the toolbar.", section = pinnedKeys, position = 13)
	default boolean pin8() { return false; }

	@ConfigItem(keyName = "pin9", name = "Pin 9",
		description = "Show 9 on the toolbar.", section = pinnedKeys, position = 14)
	default boolean pin9() { return false; }

	// No Lombok here: SwingPanelHost (Kotlin) reads getScale(), and Kotlin compiles
	// against these sources before Lombok has generated anything.
	enum SidebarScale
	{
		P100("100%", 1f),
		P90("90%", 0.9f),
		P80("80%", 0.8f),
		P70("70%", 0.7f),
		P60("60%", 0.6f);

		private final String label;
		private final float scale;

		SidebarScale(String label, float scale)
		{
			this.label = label;
			this.scale = scale;
		}

		public float getScale()
		{
			return scale;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = SIDEBAR_SCALE,
		name = "Sidebar scale",
		description = "Size of sidebar panels. Lower values draw panels smaller so more fits on screen.",
		position = 1
	)
	default SidebarScale sidebarScale()
	{
		return SidebarScale.P100;
	}
}
