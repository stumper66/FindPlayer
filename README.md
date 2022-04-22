# FindPlayer

Minecraft Java Plugin

Use this plugin on your minecraft spigot server to add a command to locate players on your server.
It will show their coordinates, world and more. If the target user is offline (and logging is
enabled) it will show where they last were.

Includes optional integration into WorldGuard and will display whichever regions the player is in.

## Configuration

Nothing special needed. Just place the .jar file into your plugins directory and restart the server.
By default it will log to a json file so player's last location will be logged. Logging options
include: mysql, sqlite, json or none.

default configuration:
![config](/images/config1.png)

The online and offline player messages are fully customizeable to show only the items you want to
see, change colors and more.

## Permissions

* FindPlayer.findp - basic permission to allow looking up player's locations
* FindPlayer.reload - reload the configuration
* FindPlayer.purge - delete any cached entries from memory and disk

## Examples:

![example1](/images/image1.png)
![example2](/images/image2.png)

## Misc

Credit goes out to [ellienntatar] for making the original [FindPlayer]. I wanted to improve on it so
make this version from the original concept.

[ellienntatar]: https://www.spigotmc.org/resources/authors/ellienntatar.886090/

[FindPlayer]: https://www.spigotmc.org/resources/findplayer.75642/