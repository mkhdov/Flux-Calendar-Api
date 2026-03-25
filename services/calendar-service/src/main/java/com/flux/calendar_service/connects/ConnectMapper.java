package com.flux.calendar_service.connects;

import org.springframework.stereotype.Component;
import com.flux.calendar_service.connects.dto.ConnectResponse;
import com.flux.calendar_service.connects.dto.ConnectResponsePerUser;

@Component
public class ConnectMapper {
    public ConnectResponse toConnectResponse(Connect connect) {
        return new ConnectResponse(
                connect.getUserId(),
                connect.isGoogle(),
                connect.isZoom()
        );
    }

    public ConnectResponsePerUser toConnectResponsePerUser(Connect connect) {
        return new ConnectResponsePerUser(connect.isGoogle(), connect.isZoom());
    }

}
