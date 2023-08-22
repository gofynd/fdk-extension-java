package com.fynd.extension.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class User {

    String _id;

    String UID;

    String UserName;

    String FirstName;

    String LastName;

    String Gender;

    String AccountType;

    String Image;

    List<String> Roles;

    Boolean Active;

    String ProfilePicURL;

    Date CreatedAt;

    Boolean HasOldPasswordHash;

    Date UpdatedAt;

    String Hash;

    Debug Debug;
}

