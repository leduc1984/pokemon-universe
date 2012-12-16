package main

import (
	"bytes"
	"fmt"
	"net"
	"os"
	"container/list"
	"sync"
	
	puh "puhelper"
)

type Server struct {
	port int
	clients map[int]*Client
	tileChangeChan chan *Packet
	tileLock sync.Mutex
}

func NewServer(_port int) *Server {
	return &Server{port: _port,
		clients:        make(map[int]*Client),
		tileChangeChan: make(chan *Packet)}
}

func (s *Server) RunServer() {
	sock, err := net.Listen("tcp", ":" + fmt.Sprintf("%d", s.port))
	if err != nil {
		fmt.Printf("Server error: %v", err)
		os.Exit(1)
	}
	
	go s.HandleTileChange()
	fmt.Printf("[Succeeded]\nWaiting for clients\n")

	for {
		clientsock, err := sock.Accept()
		if err != nil {
			fmt.Printf("Server error: %v", err)
			break
		}
		
		client := NewClient(clientsock, s.tileChangeChan)
		fmt.Printf("Client connected: %d\n", client.id)
		
		s.clients[client.id] = client

		go client.HandleClient()
	}
	sock.Close()
}

func (s *Server) HandleTileChange() {
	for {
		s.tileLock.Lock()
		var query bytes.Buffer
		
		//query.WriteString("DECLARE @TileID INT;\n")
		
		packet := <-s.tileChangeChan
		
		if packet == nil {
			break
		}

		numTiles := int(packet.ReadUint16())
		if numTiles <= 0 { // Zero tile selected bug
			return
		}

		updatedTiles := list.New()

		for i := 0; i < numTiles; i++ {
			x := int(packet.ReadInt16())
			y := int(packet.ReadInt16())
			z := int(packet.ReadUint16())
			blocking := int(packet.ReadUint16())
			numLayers := int(packet.ReadUint16())

			// Check if tile already exists
			tile, exists := g_map.GetTileFromCoordinates(x, y, z)

			if IS_DEBUG {
				fmt.Printf("Tile Exists - %v - Layers: %d\n", exists, numLayers)
			}

			if numLayers > 0 {
				if !exists { // Tile does not exists, create it		
					if IS_DEBUG {
						fmt.Printf("New Tile - X: %d - Y: %d - Z: %d\n", x, y, z)
					}

					tile = NewTileExt(x, y, z)
					tile.DbId = g_newTileId
					fmt.Printf("Current TileID: %d\n", g_newTileId)
					g_newTileId++
				} else if IS_DEBUG {
					fmt.Printf("Update Tile - X: %d - Y: %d - Z: %d - DbId: %d\n", x, y, z, tile.DbId)
				}

				// Set/update blocking
				tile.SetBlocking(blocking)
				
				// Save tile to database
				buffer := tile.Save()
				query.Write(buffer.Bytes())

				for j := 0; j < numLayers; j++ {
					layerId := int(packet.ReadUint16())
					sprite := int(packet.ReadUint16())

					tileLayer := tile.GetLayer(layerId)
					if tileLayer == nil {
					
						// Add and save new tile layer
						tileLayer = tile.AddLayer(layerId, sprite)
						
						//Save the tile layer
						buffer := tileLayer.Save()
						query.Write(buffer.Bytes())
						
						if IS_DEBUG {
							fmt.Printf("Add Layer - Tile Id: %d - Layer: %d - DbId: %d\n", tile.DbId, layerId, tileLayer.DbId)
						}
					} else {
						if sprite == 0 {
							if IS_DEBUG {
								fmt.Printf("Delete Layer - Tile Id: %d - DbId: %d\n", tile.DbId, tileLayer.DbId)
							}

							// Remove layer, this will also remove the layer from database
							tile.RemoveLayer(tileLayer)
						} else {
							if IS_DEBUG {
								fmt.Printf("Update Layer - Tile Id: %d - DbId: %d\n", tile.DbId, tileLayer.DbId)
							}

							// Update tile layer with new sprite id
							tileLayer.SetSpriteId(sprite)
						}
						
						//Save the tile layer
						buffer := tileLayer.Save()
						query.Write(buffer.Bytes())
					}
				}
			} else {
				if exists {
					if IS_DEBUG {
						fmt.Printf("Delete Tile - Tile Id: %d\n", tile.DbId)
					}

					// Remove tile from database
					
					buffer := tile.Delete()
					query.Write(buffer.Bytes())
					g_map.RemoveTile(tile)
				}
			}

			updatedTiles.PushBack(tile)
		}
		
		//Update database
		if err := puh.DBQuery(query.String()); err != nil {
			fmt.Printf("[ERROR] Save failed - error: %s\n", err) 
		}
		
		//Send the updated tiles to all clients
		s.SendTileUpdateToClients(updatedTiles, 0)
		s.tileLock.Unlock()
	}
}

func (s *Server) SendTileUpdateToClients(_tiles *list.List, _sender int) {
//	for e := _tiles.Front(); e != nil; e = e.Next() {
//		tile := e.Value.(*Tile)

		// Send to connected clients, except sender
//		for id, client := range(s.clients) {
//			if id != _sender {
//				// Send to client
//			}
//		}
//	}
}

func (s *Server) SendMapListUpdateToClients() {
	for _, client := range(s.clients) {
		client.SendMapList()
	}
}

func (s *Server) SendNpcToClients(_id int64) {
	for _, client := range(s.clients) {
		client.SendNpc(_id)
	}
}

func (s *Server) SendDeleteNpcToClients(_id int64) {
	for _, client := range(s.clients) {
		client.SendDeleteNpc(_id)
	}
}