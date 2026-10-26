# Nocturne - Fabric 1.21.4 utility client

Open the ClickGUI with RIGHT SHIFT (rebind in Controls -> Nocturne).
Tabs: Modules | Configs | Friends | Theme.

Modules tab:  LMB toggle, RMB expand settings / collapse panel, MMB bind a key (Backspace clears), drag headers, scroll.
Configs tab:  type a name + Save, Load / Delete saved configs (.minecraft/config/nocturne/configs).
Friends tab:  add nicknames - friends are green, never targeted by combat modules, protected by NoFriendsDamage.
Theme tab:    presets + custom colours, roundness, opacity, animation speed, blur, particles.

Build:  gradle build   ->  build/libs/nocturne-1.0.0.jar  (drop in mods/ together with Fabric API). Needs JDK 21.

Font:   all Nocturne text uses Inter (Regular + SemiBold) - the closest free match to Apple SF Pro used by Catlean.
        Files: assets/nocturne/font/ (inter.json, inter_bold.json, *.otf, LICENSE-Inter.txt - SIL OFL 1.1).
