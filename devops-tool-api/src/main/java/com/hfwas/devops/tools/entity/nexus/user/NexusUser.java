package com.hfwas.devops.tools.entity.nexus.user;

import lombok.Data;

import java.util.List;

@Data
public class NexusUser {

    private String emailAddress;


    private String firstName;

    private String lastName;

    private String password;

    private List<String> roles;


    private String status;

    private String userId;
}
