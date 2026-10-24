# Nocturne - Fabric 1.21.4 utility client

Open the ClickGUI with RIGHT SHIFT (rebind in Controls -> Nocturne).
Left click = toggle module / change setting, right click = expand settings / collapse panel / previous mode.
Drag the panel headers to move them. Config is saved to .minecraft/config/nocturne.json.

Build:  ./gradlew build   ->  build/libs/nocturne-1.0.0.jar  (drop in mods/ together with Fabric API)
No gradle wrapper included: run `gradle wrapper --gradle-version 8.12` once, or copy gradlew + gradle/ from the
official Fabric template (https://fabricmc.net/develop/template/). Needs JDK 21.

Modules: Hitboxes, Tracers, ESP, Nametags, AutoSprint, Fullbright, HUD.
Theme colours: src/main/java/dev/nocturne/gui/Theme.java  (+ Accent slider in the HUD module).
Add a module: extend Module, add(...) settings, register it in ModuleManager.init().
