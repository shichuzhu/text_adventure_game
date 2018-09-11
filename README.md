# CS 296-25 Honors Project (Porting still under construction)

This repository contains the code we did in class.  You can
use it as a starting point for your own project if you want.

## Installation

Do a `git pull` in your repository.

## Usage

The main file you want to edit is `src/adventure/core.clj`.

To run the program, use `lein run` from the command line.

## Command lists


## Mechanism
The adventure/adventure/core.clj file defines a framework for a generic game engine. All contents of the game is defined in the maps.json file. There are a few key concepts in the game framework, explained below.

### Quests
Quests are the goal of the game. There can be multiple quests in a hierachical structure. Finishing the main quest completes the game. The quest system is designed to provide player better hint for what they need to do in any stage in the game. Quests can be activated or finished through triggering events. They can also be activated by picking up items. To view the currently active quests, type "q(uest)".

### Inventory
Inventory is a collection of items that is bundled with the player. Type "i(ventory)" to view the items in inventory.

### Map
The world of the game consists of maps. Any top-level key in the maps.json file which does not start with an underscore is a name of the map. (Exceptions are "_configuration", "_itemAttr" and  "_quests")_. Maps are connected with each other. A player can move from a map to another using "n(orth), s(outh), e(ast), w(est)" commands. "l(ook)" command prints details of current map. A map also optionally contains pickable items, and events.

### Item
Item are objects that player can interact with in the game. Item can be either in the inventory, or in the map, or create / destroyed through triggering events. Items can also have a description, to be viewed in the game by typing "observe [item-name]". Items in the map can be "pick" to inventory, and item in the inventory can be "drop" to map.

### Events
Events belongs to certain maps. It is triggered by special "match-words" in the json file while the player is in the parental map. To trigger the events some items must be present in the inventory, specified by the "require" field in the json file. Events also have "action" field in the json configuration, which are raw Clojure codes to be evaluated at runtime to modify the map and player state properties.

## A series of commands to start the sample game
l
get I card
n
l
get raw-egg
q
n
l
get handout
i
observe handout
tree
s
l
w

## Ongoing works
Add network functionality so that it allows collaboration in playing the game

## License

Copyright © 2016 Mattox Beckman, free for personal and educational use.

## Network

### Client Processor:
1. wait for command input
2. pre-process (concurrent)
	1. if command not read-only: ask host node for permission to make change, loop until acquired the lock. The lock should come piggybacked with the latest state.
	2. sync pending changes
3. Proc the command
4. Push change to host and return the lock

### Host sync: (does FP change the way of this idea?)
* loop accept a pending request for the lock and add to the queue
* if the request is not read-only
	1. send the lock along with the latest state.
	2. receive the pushed change.
	3. regain the lock
* Otherwise
	1. send the latest state
