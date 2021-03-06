package com.domy.zoomanagement.controllers;

import com.domy.zoomanagement.managers.GameManager;
import com.domy.zoomanagement.models.*;
import com.domy.zoomanagement.repository.AnimalsRepository;
import com.domy.zoomanagement.repository.CaretakerRepository;
import com.domy.zoomanagement.repository.EnclosureRepository;
import com.domy.zoomanagement.repository.RoomRepository;
import com.domy.zoomanagement.requests.RoomResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.domy.zoomanagement.controllers.CaretakerController.CARETAKER_NOT_FOUND;
import static com.domy.zoomanagement.controllers.EnclosureController.ENCLOSURE_NOT_FOUND;
import static com.google.common.primitives.Longs.asList;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    static final String ROOM_NOT_FOUND = "Room with given ID wasn't found";
    private AnimalsRepository animalsRepository;
    private RoomRepository roomRepository;
    private CaretakerRepository caretakerRepository;
    private EnclosureRepository enclosureRepository;
    private GameManager gameManager;

    @Autowired
    public RoomController(AnimalsRepository animalsRepository, RoomRepository roomRepository, CaretakerRepository caretakerRepository, EnclosureRepository enclosureRepository, GameManager gameManager) {
        this.animalsRepository = animalsRepository;
        this.roomRepository = roomRepository;
        this.caretakerRepository = caretakerRepository;
        this.enclosureRepository = enclosureRepository;
        this.gameManager = gameManager;
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping(produces = {"application/json"})
    public @ResponseBody
    List<RoomResponse> getRooms(@RequestParam(required = false) Boolean bought) {
        List<Room> rooms = bought == null?
                roomRepository.findAll():
                roomRepository.findAll(bought);
        return parseToRoomResponses(rooms);
    }

    private List<RoomResponse> parseToRoomResponses(List<Room> rooms){
        return rooms.stream().map(room -> RoomResponse.builder()
                .id(room.getId())
                .bought(room.isBought())
                .localization(room.getLocalization())
                .locatorsMaxNumber(room.getLocatorsMaxNumber())
                .caretakerId(room.getCaretaker() != null?
                        room.getCaretaker().getId()
                        :null)
                .enclosureId(room.getEnclosure() != null?
                        room.getEnclosure().getId()
                        :null)
                .species(room.getSpecies().stream().map(Species::getName).collect(Collectors.toList()))
                .surface(room.getSurface())
                .price(room.getPrice())
                .build()).collect(Collectors.toList());
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping(value = "/{roomId}/animals")
    public @ResponseBody
    List<Animal> getAnimalsInRoom(@PathVariable Long roomId) {
        return animalsRepository.findAllByRoom(roomId).orElse(null);
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping(value = "/available/{speciesName}")
    public @ResponseBody
    List<Long> getAvailableRoomsForSpecies(@PathVariable String speciesName) {
        return roomRepository.findAvailableForSpecies(speciesName)
                .map(rooms -> rooms.stream()
                        .filter(room -> roomRepository.getNumberOfOccurrencesPlaces(room.getId()) < room.getLocatorsMaxNumber())
                        .map(Room::getId)
                        .collect(Collectors.toList()))
                .orElse(asList());
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @GetMapping(value = "/{roomId}")
    public @ResponseBody
    Room getRoomInfo(@PathVariable Long roomId) {
        return roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException(ROOM_NOT_FOUND));
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @PatchMapping("{roomId}/enclosure")
    public Room updateEnclosure(@PathVariable Long roomId, @RequestBody Long enclosureId) {

        Enclosure enclosure = enclosureRepository.findById(enclosureId)
                .orElseThrow(() -> new IllegalArgumentException((ENCLOSURE_NOT_FOUND)));

        if (!enclosure.isBought()) throw new IllegalStateException("Enclosure is not bought!");

        return roomRepository.findById(roomId).map(room -> {
            room.setEnclosure(enclosure);
            return roomRepository.save(room);
        }).orElseThrow(() -> new ResourceNotFoundException(ROOM_NOT_FOUND));
    }

    @CrossOrigin(origins = "http://localhost:4200")
    @PatchMapping("{roomId}/caretaker")
    public Room updateCaretaker(@PathVariable Long roomId, @RequestBody Long caretakerId) {

        Caretaker caretaker = caretakerRepository.findById(caretakerId)
                .orElseThrow(() -> new IllegalArgumentException((CARETAKER_NOT_FOUND)));

        return roomRepository.findById(roomId).map(room -> {
            room.setCaretaker(caretaker);
            return roomRepository.save(room);
        }).orElseThrow(() -> new ResourceNotFoundException(ROOM_NOT_FOUND));
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @PatchMapping("{roomId}/buy")
    public Room buyRoom(@PathVariable Long roomId, @RequestBody String localization) {
        return roomRepository.findById(roomId).map(room -> {
            if (room.isBought()) throw new IllegalStateException("Room already bought");
            room.setBought(true);
            room.setLocalization(localization);
            roomRepository.save(room);
            gameManager.buy(room.getPrice());
            return room;
        }).orElseThrow(() -> new ResourceNotFoundException((ROOM_NOT_FOUND)));
    }


    @CrossOrigin(origins = "http://localhost:4200")
    @PatchMapping("/{roomId}/destroy")
    public Room deleteRoom(@PathVariable Long roomId) {
        return roomRepository.findById(roomId).map(room -> {
            if (!room.isBought()) throw new IllegalStateException("Room is not bought");
            animalsRepository.findAllByRoom(roomId)
                    .ifPresent(animals -> {throw new IllegalStateException("There are animals in this room!");});
            room.setBought(false);
            room.setCaretaker(null);
            room.setEnclosure(null);
            return roomRepository.save(room);
        }).orElseThrow(() -> new ResourceNotFoundException((ROOM_NOT_FOUND)));
    }
}
