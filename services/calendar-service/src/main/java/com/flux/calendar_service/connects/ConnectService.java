package com.flux.calendar_service.connects;

import com.flux.calendar_service.connects.dto.ConnectResponse;
import com.flux.calendar_service.connects.dto.ConnectResponsePerUser;
import com.flux.calendar_service.exception.DuplicateResourceException;
import com.flux.calendar_service.exception.ResourceNotFoundException;
import com.flux.calendar_service.exception.ServiceUnavailableException;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConnectService {
    private final ConnectRepository connectRepository;
    private final ConnectMapper connectMapper;
    public void create(
            @NotBlank(message = "User id is not provided!")
            String userId) {
        try{
            Connect connect = Connect.builder()
                    .userId(userId)
                    .google(false)
                    .zoom(false)
                    .build();
            connectRepository.save(connect);
        }catch (RuntimeException e) {
            throw new ServiceUnavailableException(e.getMessage());
        }
    }
    public void connectGoogle(
            @NotBlank(message = "User id is not provided!")
            String userId) {
        Connect connect = connectRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connect with this User ID is not found!"));
        if (connect.isGoogle()) {
            return;
        }
        connect.setGoogle(true);
        connectRepository.save(connect);
    }

    public void connectZoom(
            @NotBlank(message = "User id is not provided!")
            String userId) {
        Connect connect = connectRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connect with this User ID is not found!"));

        if (connect.isZoom()) {
            return;
        }
        connect.setZoom(true);
        connectRepository.save(connect);
    }

    public void disconnectGoogle(
            @NotBlank(message = "User id is not provided!")
            String userId) {
        Connect connect = connectRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connect with this User ID is not found!"));
        if (!connect.isGoogle()) {
            return;
        }
        connect.setGoogle(false);
        connectRepository.save(connect);
    }

    public void disconnectZoom(
            @NotBlank(message = "User id is not provided!")
            String userId) {
        Connect connect = connectRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Connect with this User ID is not found!"));

        if (!connect.isZoom()) {
            return;
        }
        connect.setZoom(false);
        connectRepository.save(connect);
    }

    public ConnectResponse getConnectById(String id) {
        return connectRepository.findById(id)
                .map(connectMapper::toConnectResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Connect by this ID is not found!"));
    }

    public ConnectResponsePerUser getConnectByUserId(String userId) {
        return connectRepository.findByUserId(userId)
                .map(connectMapper::toConnectResponsePerUser)
                .orElseThrow(() -> new ResourceNotFoundException("Connect by this ID is not found!"));
    }
}
