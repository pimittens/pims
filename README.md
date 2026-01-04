# pims

pims is a Maplestory server emulator based on Cosmic (v83 GMS). The primary goal of pims is to provide a more immersive singleplayer experience through the use of character bots which emulate human players.

## Bot Related Features

### Bot Creation

* When the server is launched, bot accounts will be created. This may take some time, particularly on the first launch. Currently a maximum of 1000 accounts will be created.
* Each bot account will have one character. The appearance of the character will be random. The "normal" skin color is more common than the others, but otherwise everything is equally likely.
* Character names are generated from a list of nouns, additional characters are added at the end if the name is already taken.
* Bot characters will always be explorers. Knights of Cygnus and Arans will probably not be implemented for bot characters.
* The server identifies bot accounts by checking that the account name starts with "bot", so the player should avoid this when creating account names.

### Bot Tasks

* The ManageBotLoginsTask runs 1 minute after launch and every 15 minutes thereafter. This task first logs out bots if more than 5 are logged in within the same 5 level bracket (for example levels 16 to 20). Next, bots are logged in up to 5 in each 5 level bracket. The brackets 1-5 and 6-10 are ignored until the end, at which point bots level 10 and under are logged in until there are 200 total bots online.
* The UpdateBotsTask runs every 500 milliseconds and attempts to update each bot depending on what they are doing.
* There is a task that creates a party and starts a KPQ instance every 30 minutes. Other PQs are not yet implemented.
* Bots have several modes. They can manage their inventory (create space if needed and equips items that are better than what they are currently using), kill monsters (maps are chosen randomly from premade lists), or do PQs (currently only KPQ and LPQ are implemented). More modes are planned.
* Bots will gain equipment as if buying them from an npc at lower levels.

### Followers

* Players can create follower bots using the @follower command. These bots will follow the player character when changing maps. Each player may only have up to 5 followers at a time.
* Follower bots can be added to the player's party using the @party command.
* Follower bots can be dismissed using the @dismiss command.
* Players can automate grinding and PQs. This uses a bot assigned to the player's character, although the bot's actions will not be displayed on the player's client. Using the @automate command toggles this feature on or off. Having the feature toggled on charges the player a number of mesos equal to their level squared every 10 seconds. This feature will work for Cygnus Knight characters, but not Arans (in general Arans are ignored).
* Using the @reqpq command will request bots to join the player's party in order to participate in a specific party quest. The bots will be randomly selected from those which are in the appropriate level range and not already logged in.
* The @toggleloot command will toggle whether the followers loot items.

## Other Features/Changes

* All characters (including bots) start with max inventory slots.
* Cygnus Knights give increased blessing of fairy levels so that a lv 120 CK will max blessing of fairy for other characters on the account.
* Several commands moved to gm level 0 (goto, whatdropsfrom, whodrops), some commands improved (gacha, whatdropsfrom), added save command.
* Cash item equips no longer expire.
* Characters have an individual exp rate which can be changed using the @exp command. The maximum exp rate is currently level / 10.
* Players can receive a medal with bonus stats based on the number of monster cards they have collected. Medals are given by the Agent E npc (this npc has some other leftover custom features which still need to be changed/removed). The stats of the medals scale linearly up to +1000 all stat, +10000 hp/mp, +100 att/m.att (might be changed). The main purpose of the medals is to remove the need for hp washing and assigning points to secondary stats.
* PQ rewards have been changed, removing some useless items and adding useful scrolls. Some notable examples: LMPQ has shield att. and shield m. att. scrolls.

## Planned Features

* Bot modes: bosses, socializing (hang out in Henesys, maybe play minigames).
* More realistic bot actions (movement and attacks). Currently bots ignore footholds/ladders/jumping since this is too much work, and packets are sometimes sent too quickly.
* Bots creating/joining guilds.
* Random bot chat messages and smegas.
* Free market (in progress, currently all items collected by bots and not equipped are sold and will appear in fm shops).
* Include a way for bots to get random cash item equips and wear them.
* Bots should use maker to create high level equipment and use scrolls.
* Currently there is no way to gain nx cash. The planned system is gaining fame when defeating bosses and exchanging fame as a currency for nx cash and other items.
* Some kind of system for combining equipment which has been scrolled into a single more powerful piece of equipment.

## Known Issues

* Attempting to log out bots while the server is running causes a deadlock.
* Sometimes bots gets stuck with the wrong weapon after the first job advancement and cannot attack.
* Some of the custom npcs should be removed or changed.