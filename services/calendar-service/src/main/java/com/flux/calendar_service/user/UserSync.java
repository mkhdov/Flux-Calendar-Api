package com.flux.calendar_service.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_sync")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserSync {
    @Id
    private String id;
    private String email;
    private String firstname;
    private String lastname;
}
