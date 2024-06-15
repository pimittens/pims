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
* Bots have several modes. They can manage their inventory (create space if needed and equips items that are better than what they are currently using), or kill monsters (maps are chosen randomly from premade lists). More modes are planned.

### Followers

* Players can create follower bots using the @follower command. These bots will follow the player character when changing maps. Each player may only have up to 5 followers at a time.
* Follower bots can be added to the player's party using the @party command.
* Follower bots can be dismissed using the @dismiss command.

## Other Features/Changes

* All characters (including bots) start with max inventory slots.
* Cygnus Knights give increased blessing of fairy levels so that a lv 120 CK will max blessing of fairy for other characters on the account.
* Several commands moved to gm level 0 (goto, whatdropsfrom, whodrops), some commands improved (gacha, whatdropsfrom), added save command.
* Cash item equips no longer expire.

## Planned Features

* Bot modes: PQs, bosses, socializing (hang out in Henesys, maybe play minigames).
* More realistic bot actions (movement and attacks). Currently bots ignore footholds/ladders/jumping since this is too much work, and packets are sometimes sent too quickly.
* Bots creating/joining guilds.
* Random bot chat messages and smegas.
* Free market: bots should create their own shops and buy items from other shops.
* Bots should spend their nx on gachapon tickets and use them at a random gachapon.
* Include a way for bots to get random cash item equips and wear them.
