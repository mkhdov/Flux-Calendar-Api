package com.flux.calendar_service.connects;

import com.flux.calendar_service.connects.dto.ConnectResponse;
import com.flux.calendar_service.connects.dto.ConnectResponsePerUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/connects")
@RequiredArgsConstructor
public class ConnectController {
    private final ConnectService connectService;

    @GetMapping("/id/{id}")
    public ResponseEntity<ConnectResponse> getConnectById(@PathVariable String id) {
        return ResponseEntity.ok().body(connectService.getConnectById(id));
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<ConnectResponsePerUser> getConnectUserPerId(@PathVariable String userId) {
        return ResponseEntity.ok().body(connectService.getConnectByUserId(userId));
    }
}