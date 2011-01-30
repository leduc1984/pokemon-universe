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
	punet "network"
)

type PU_GameProtocol struct {
}

func NewGameProtocol() *PU_GameProtocol {
	return &PU_GameProtocol{}
}

func (p *PU_GameProtocol) ProcessPacket(_packet *punet.Packet) {
	header := _packet.ReadUint8()
	switch header {
		case punet.HEADER_PING:
			p.ReceivePing()
			
		case punet.HEADER_LOGIN:
			p.ReceiveLoginStatus(_packet)
			
		case punet.HEADER_IDENTITY:
			p.ReceiveIdentity(_packet)
			
		case punet.HEADER_TILES:
			p.ReceiveTiles(_packet)
			
		//Headers that are not punet constants are headers that might change.
		//Currently they are used to make the client compatible with the 
		//old puserver.
		case 0xB1:
			NewCreatureMoveMessage(_packet)
	
		case 0xB2:
			if g_game.self != nil {
				g_game.self.CancelWalk()
			}
			
		case 0xB3:
			NewWarpMessage(_packet)
			
		case 0xB4:
			NewCreatureTurnMessage(_packet)
			
		case 0x03:
			p.ReceiveTilesRefreshed()
	}
}

func (p *PU_GameProtocol) ReceivePing() {
	message := NewPingMessage()
	g_conn.SendMessage(message)
}

func (p *PU_GameProtocol) ReceiveLoginStatus(_packet *punet.Packet) {
	g_conn.loginStatus = int(_packet.ReadUint8())
}

func (p *PU_GameProtocol) ReceiveIdentity(_packet *punet.Packet) {
	message := NewIdentityMessage(_packet)
	g_map.AddCreature(message.player)
	g_game.self = message.player
}

func (p *PU_GameProtocol) ReceiveTiles(_packet *punet.Packet) {
	NewTilesMessage(_packet)
}

func (p *PU_GameProtocol) ReceiveTilesRefreshed() {
	g_game.state = GAMESTATE_WORLD
}

func (p *PU_GameProtocol) SendLogin(_username string, _password string) {
	message := NewLoginMessage()
	message.username = _username
	message.password = _password
	message.version = CLIENT_VERSION
	g_conn.SendMessage(message)
} 

func (p *PU_GameProtocol) SendRequestLoginPackets() {
	message := NewLoginRequestMessage()
	g_conn.SendMessage(message)
}

func (p *PU_GameProtocol) SendMove(_direction int, _requestTiles bool) {
	message := NewMoveMessage()
	message.direction = _direction
	message.requestTiles = _requestTiles
	g_conn.SendMessage(message)
}

func (p *PU_GameProtocol) SendTurn(_direction int) {
	message := NewTurnMessage()
	message.direction = _direction
	g_conn.SendMessage(message)
}