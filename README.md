# Hunger Horror (Fabric, Minecraft 1.21.1)

When your hunger bar drops to 4 or lower, a tall, fast monster ("The Hunger", custom 3D model with glowing eyes)
spawns far away. Your screen turns red with RUN RUN RUN and the distance to the monster in blocks.
It screams (louder and more often the closer it is) and kills instantly: "<Nick> Made a Mistake".
The closer it gets, the redder and faster the screen pulses.
Eat and it appears right next to you, stares at you for a few seconds, then vanishes in smoke.

Config: `config/hungerhorror.json` (hunger threshold, monster speed, spawn distance, cooldown, red screen on/off).
The mod must be installed on the server AND on the players' clients (custom entity).

## Build
Push to GitHub, open Actions -> build -> download the `hunger-horror` artifact (the jar).
Needs Fabric Loader + Fabric API on 1.21.1.
