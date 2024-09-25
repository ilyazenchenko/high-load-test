package ru.zenchenko.nbki_test.controllers;

import jakarta.persistence.EntityManager;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.zenchenko.nbki_test.model.User;
import ru.zenchenko.nbki_test.repository.UserRepository;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;


    //получить всех юзеров
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        return new ResponseEntity<>(userRepository.findAll(), HttpStatus.OK);
    }

    //получить юзера по id
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id){
        Long userID = tryParseLong(id);
        if (userID == null) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        Optional<User> user = userRepository.findById(userID);
        if (user.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(user.get(), HttpStatus.OK);
    }

    //получить кол-во юзеров в таблице
    @GetMapping ("/count")
    public Long getRowsCount() {
        return userRepository.count();
    }

    //получить юзеров с id между start и start + range
    @GetMapping("/range")
    public ResponseEntity<List<User>> getRangeUsers(@RequestParam("start") String start, @RequestParam("range") String rangeStr){
        Long startingId = tryParseLong(start);
        Long range = tryParseLong(rangeStr);
        if (startingId == null || range == null) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        List<User> users = userRepository.findAllByIdBetween(startingId, startingId + range - 1);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    //создать пользователя
    @PostMapping
    public ResponseEntity<HttpStatus> createUser(@RequestBody User user) {
        userRepository.save(user);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    //удалить пользователя
    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> deleteUser(@PathVariable String id) {
        Long userID = tryParseLong(id);
        if (userID == null) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        userRepository.deleteById(userID);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    //изменить юзера
    @PatchMapping("/{id}")
    public ResponseEntity<HttpStatus> changeUserName(@PathVariable String id, @RequestBody User user) {
        Long userID = tryParseLong(id);
        if (userID == null) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
        Optional<User> userFromDB = userRepository.findById(userID);
        if (userFromDB.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        userFromDB.get().setName(user.getName());
        userRepository.save(userFromDB.get());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    //если не парсится в Long, возвращается null
    private Long tryParseLong(String id) {
        long userID;
        try {
            userID = Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
        return userID;
    }

}
