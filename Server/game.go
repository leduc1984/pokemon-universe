/*Pokemon Universe MMORPG
Copyright (C) 2010 the Pokemon Universe Authors

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.*/
package main

import (
	"sync"
)

type GameState int
const (
	GAME_STATE_STARTUP GameState = iota
	GAME_STATE_INIT
	GAME_STATE_NORMAL
	GAME_STATE_CLOSED
	GAME_STATE_CLOSING
)

type Game struct {
	State		GameState
	Creatures	CreatureList
	Players		CreatureList
	
	WorldMap	*Map
	Locations	*LocationStore
	
	mutexCreatureList	*sync.RWMutex
	mutexPlayerList		*sync.RWMutex	
}

func NewGame() *Game {
	game := Game{}
	game.State = GAME_STATE_STARTUP
	// Initialize maps
	game.Creatures = make(CreatureList)
	game.Players = make(CreatureList)
	
	return &game
}

func (the *Game) Load() (LostIt bool) {
	LostIt = true // fuck >:(
	the.WorldMap = NewMap()
	the.Locations = NewLocationStore()
	
	g_logger.Println(" - Loading locations")
	// Load locations
	if err := the.Locations.Load(); err != nil {
		g_logger.Println(err)
		LostIt = false
	}
	
	// Load worldmap
	g_logger.Println(" - Loading worldmap")
	if err := the.WorldMap.Load(); err != nil {
		g_logger.Println(err)
		LostIt = false
	}
	
	return
}

func (g *Game) GetPlayerByName(_name string) ICreature {
	for _, value := range g.Players {
		if value.GetName() == _name {
			return value
		}
	}
	
	return nil
}

func (g *Game) AddCreature(_creature ICreature) {
	// TODO: Maybe only take the creatues from the area the new creature is in. This saves some extra iterating
	// TODO 2: Upgrade this to parallel stuff
	for _, value := range g.Creatures {
		value.OnCreatureAppear(_creature, true)
	}
	
	g.Creatures[_creature.GetUID()] = _creature
	
	if _creature.GetType() == CTYPE_PLAYER {
		g.Players[_creature.GetUID()] = _creature
	}
}

func (g *Game) RemoveCreature(_guid uint64) {
	object, exists := g.Creatures[_guid]
	if exists {
		g.mutexCreatureList.Lock()
		defer g.mutexCreatureList.Unlock()
		
		g.Creatures[_guid] = nil, false
		
		if object.GetType() == CTYPE_PLAYER {
			g.mutexPlayerList.Lock()
			defer g.mutexPlayerList.Unlock()
			g.Players[_guid] = nil, false
		}
	}
}


func (g *Game) OnPlayerMove(_creature ICreature, _direction uint16, _sendMap bool) {
	ret := g.OnCreatureMove(_creature, _direction)
	
	player := _creature.(*Player)
	if ret == RET_NOTPOSSIBLE {
		player.sendCreatureMove(_creature, _creature.GetTile(), _creature.GetTile())		
	} else if ret == RET_PLAYERISTELEPORTED {
		player.sendPlayerWarp()
		player.sendMapData(DIR_NULL)
	} else {
		player.sendMapData(_direction)
	}
}

func (g *Game) OnPlayerTurn(_creature ICreature, _direction uint16) {
	if _creature.GetDirection() != _direction {
		g.OnCreatureTurn(_creature, _direction)
	}
}

func (g *Game) OnCreatureMove(_creature ICreature, _direction uint16) (ret ReturnValue) {
	ret = RET_NOTPOSSIBLE
	
	if !CreatureCanMove(_creature) {
		return
	}
	
	currentTile := _creature.GetTile()
	destinationPosition := currentTile.Position
	
	switch(_direction) {
		case DIR_NORTH:
			destinationPosition.Y -= 1
		case DIR_SOUTH:
			destinationPosition.Y += 1
		case DIR_WEST:
			destinationPosition.X -= 1
		case DIR_EAST:
			destinationPosition.X += 1
	}
	
	// Check if destination tile exists
	destinationTile, ok := g.WorldMap.GetTileFromPosition(destinationPosition)
	if !ok {
		return		
	}
	
	// Check if we can move to the destination tile
	if ret = destinationTile.CheckMovement(_creature, _direction); ret == RET_NOTPOSSIBLE {
		return
	}
	
	// Tell creatures this creature has moved
	g.mutexCreatureList.RLock()
	defer g.mutexCreatureList.RUnlock()
	for _, value := range g.Creatures {
		if value != nil {
			value.OnCreatureMove(_creature, currentTile, destinationTile, false)
		}
	}
	
	// Move creature object to destination tile
	if ret = currentTile.RemoveCreature(_creature); ret == RET_NOTPOSSIBLE {
		return
	}
	if ret = destinationTile.AddCreature(_creature); ret == RET_NOTPOSSIBLE {
		currentTile.AddCreature(_creature) // Something went wrong, put creature back on old tile
		return
	}
	
	// Player is not teleported so we set his new location here
	if ret != RET_PLAYERISTELEPORTED {
		_creature.SetTile(destinationTile) 
	}
	
	// If ICreature is a player type we can check for wild encounter
	g.checkForWildEncounter(_creature, destinationTile)
	
	return
}

func (g *Game) OnCreatureTurn(_creature ICreature, _direction uint16) {
	if _creature.GetDirection() != _direction {
		_creature.SetDirection(_direction)
		
		visibleCreatures := _creature.GetVisibleCreatures()
		for _, value := range(visibleCreatures) {
			value.OnCreatureTurn(_creature)
		}
	}
}

func (g *Game) checkForWildEncounter(_creature ICreature, _tile *Tile) {
	if _creature.GetType() == CTYPE_PLAYER {
		// Do some checkin'
	}
}