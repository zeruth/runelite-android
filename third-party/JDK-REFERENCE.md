# OpenJDK reference checkouts

Created by `python tools-src/fetch-jdk-reference.py` (or `tools-src/fetch-jdk-reference.ps1`).
Both directories are gitignored; re-run the script to refresh them to the latest `-ga` tag.

| Directory | Repository | Tag | Commit |
|---|---|---|---|
| `third-party/jdk8u` | https://github.com/openjdk/jdk8u | `jdk8u512-ga` | `b661ee6ff840d6922aa7424a4d8f24760b21cf26` |
| `third-party/jdk17u` | https://github.com/openjdk/jdk17u | `jdk-17.0.21-ga` | `e8adc958475b92b9f251145c6ce20532be16d806` |

## Sparse paths

`jdk8u`:

- `jdk/src/share/classes/java/awt`
- `jdk/src/share/classes/java/beans`
- `jdk/src/share/classes/java/applet`
- `jdk/src/share/classes/javax/swing`
- `jdk/src/share/classes/javax/imageio`
- `jdk/src/share/classes/javax/sound`
- `jdk/src/share/classes/javax/accessibility`
- `jdk/src/share/classes/javax/print`
- `jdk/src/share/classes/sun/awt`
- `jdk/src/share/classes/sun/java2d`
- `jdk/src/share/classes/sun/font`
- `jdk/src/share/classes/sun/swing`
- `jdk/src/share/classes/com/sun/imageio`
- `jdk/src/share/classes/com/sun/media/sound`
- `jdk/src/share/native/sun/awt`
- `jdk/src/share/native/sun/java2d`
- `jdk/src/share/native/sun/font`

`jdk17u`:

- `src/java.desktop/share/classes`
- `src/java.net.http/share/classes`

## How the checkouts are used

`jdk8u` is the faithful source for the public API the `android-awt` shadow surface ports:
when a card ports a class, start from the OpenJDK 8u file, keep its public and protected
signatures and Javadoc-visible behaviour, replace `sun.*` peer coupling with the module's own
`net.runelite.awt` backend, and keep the OpenJDK license header on the ported file. The native
sources under `jdk/src/share/native` document the behaviour of the C side the Java classes
lean on (Java2D loops, font rasterization). `jdk17u` supplies the JDK 9+ members that RuneLite
and Plugin Hub plugins use on top of the 8u surface (marked `// JDK 9+` in ported files) and
`java.net.http` for reference.

The checkouts are reference and porting source only. Nothing in them is compiled, dexed,
linked or shipped as-is; only hand-ported files under `android-awt/src` enter the build.

## License

OpenJDK is Copyright (c) Oracle and/or its affiliates and contributors, licensed under the
GNU General Public License, version 2, with the Classpath Exception (GPLv2+CE). Each checkout
carries the full text in its `LICENSE` and `ASSEMBLY_EXCEPTION` files. Files ported from these
trees into `android-awt` retain their original copyright and license header and are
distributed under the same terms; the Classpath Exception is what permits linking them with
the independently licensed code of this project.
